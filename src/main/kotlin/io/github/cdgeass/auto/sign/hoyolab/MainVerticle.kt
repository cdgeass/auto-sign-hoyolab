package io.github.cdgeass.auto.sign.hoyolab

import io.vertx.core.*
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject

fun main(args: Array<String>) {
  val vertx = Vertx.vertx()

  vertx.deployVerticle(MainVerticle())
  vertx.deployVerticle(ConfigVerticle())
  vertx.deployVerticle(SignVerticle())

  Runtime.getRuntime().addShutdownHook(Thread {
    vertx.deploymentIDs().forEach { vertx.undeploy(it) }
  })
}

class MainVerticle : AbstractVerticle() {

  private lateinit var eb: EventBus

  override fun start(startPromise: Promise<Void>?) {
    eb = vertx.eventBus()

    vertx.setTimer(1000) {
      loadConfig()
        .compose {
          val actId = it.getString("act_id")
          val cookies = it.getJsonArray("cookies")

          val signFutureList = cookies.map { cookie ->
            sign(cookie as String, actId)
          }

          CompositeFuture.all(signFutureList)
        }
    }
  }

  private fun loadConfig(): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    eb.request<JsonObject>("load.config", "") {
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

    val json = JsonObject(
      """
        {
          "act_id": "$actId",
          "cookie": "$cookie"
        }
      """
    )

    eb.request<JsonObject>("sign", json) {
      if (it.succeeded()) {
        promise.complete(it.result().body())
      } else {
        promise.fail(it.cause())
      }
    }

    return promise.future()
  }
}

