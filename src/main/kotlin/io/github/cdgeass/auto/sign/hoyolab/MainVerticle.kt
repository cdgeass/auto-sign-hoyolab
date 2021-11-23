package io.github.cdgeass.auto.sign.hoyolab

import com.diabolicallabs.vertx.cron.CronEventSchedulerVertical
import io.vertx.core.*
import io.vertx.core.eventbus.EventBus
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val vertx = Vertx.vertx()

  vertx.deployVerticle(CronEventSchedulerVertical())

  vertx.deployVerticle(MainVerticle())
  vertx.deployVerticle(ConfigVerticle())
  vertx.deployVerticle(SignVerticle())

  // register schedule
  vertx.eventBus().request<JsonObject>(
    "cron.schedule", JsonObject(
      """
      {
        "cron_expression": "0 0 8 * * ? *",
        "timezone_name": "Asia/Shanghai",
        "address": "auto.start",
        "message": {},
        "repeat": true,
        "action": "send"
      }
      """
    )
  ) {
    if (it.failed()) {
      it.cause().printStackTrace()
      exitProcess(-1)
    }
  }

  vertx.eventBus().request<JsonObject>("auto.start", "") {}

  Runtime.getRuntime().addShutdownHook(Thread {
    vertx.deploymentIDs().forEach { vertx.undeploy(it) }
  })
}

class MainVerticle : AbstractVerticle() {

  private val logger = LoggerFactory.getLogger(MainVerticle::class.java)

  private lateinit var eb: EventBus

  override fun start(startPromise: Promise<Void>?) {
    eb = vertx.eventBus()

    eb.consumer<Void>("auto.start") {
      logger.info("开始签到")

      lateinit var signFutureList: List<Future<JsonObject>>
      loadConfig()
        .compose {
          val actId = it.getString("act_id")
          val cookies = it.getJsonArray("cookies")

          signFutureList = cookies.map { cookie ->
            sign(cookie as String, actId)
          }

          CompositeFuture.join(signFutureList)
        }
        .onComplete {
          signFutureList.forEach { future ->
            future.onSuccess { msg ->
              val nickName = msg.getString("nickname")
              val totalSignDay = msg.getInteger("total_sign_day")
              logger.info("已为 $nickName 签到，共签到 $totalSignDay 天")
            }
          }

          logger.info("签到完成")
        }
    }
  }

  private fun loadConfig(): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    eb.request<JsonObject>("auto.load.config", "") {
      if (it.succeeded()) {
        promise.complete(it.result().body())
      } else {
        promise.fail(it.cause())
      }
    }
    return promise.future()
  }

  private fun sign(cookie: String, actId: String): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    val json = JsonObject()
      .put("act_id", actId)
      .put("cookie", cookie)

    eb.request<JsonObject>("auto.sign", json) {
      if (it.succeeded()) {
        promise.complete(it.result().body())
      } else {
        promise.fail(it.cause())
      }
    }

    return promise.future()
  }
}

