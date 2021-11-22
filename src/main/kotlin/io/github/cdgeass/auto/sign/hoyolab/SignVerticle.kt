package io.github.cdgeass.auto.sign.hoyolab

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.impl.headers.HeadersMultiMap
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.ext.web.codec.BodyCodec

/**
 * @author cdgeass
 * @since 2021-11-18
 */
class SignVerticle : AbstractVerticle() {
  private val HEADERS1 = HeadersMultiMap.headers()
    .addAll(
      mutableMapOf<String, String>(
        "Accept" to "application/json;charset=utf-8, text/plain, */*",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
        "Connection" to "keep-alive",
        "Host" to "bbs-api-os.mihoyo.com",
        "Origin" to "https://www.hoyolab.com",
        "Referer" to "https://www.hoyolab.com/",
      )
    )!!

  private val HEADERS2 = HeadersMultiMap.headers()
    .addAll(
      mutableMapOf<String, String>(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
        "Connection" to "keep-alive",
        "Host" to "hk4e-api-os.mihoyo.com",
        "Origin" to "https://webstatic-sea.mihoyo.com",
        "Referer" to "https://webstatic-sea.mihoyo.com/",
      )
    )!!


  private lateinit var client: WebClient

  override fun start(startPromise: Promise<Void>) {
    client = WebClient.create(vertx, WebClientOptions().setTryUseCompression(true))

    vertx.eventBus().consumer<JsonObject>("auto.sign") { msg ->
      val json = msg.body()
      val cookie = json.getString("cookie")
      val actId = json.getString("act_id")

      fetchUserInfo(cookie)
        .compose {
          println("开始为 ${it.getJsonObject("data").getJsonObject("user_info").getString("nickname")} 签到")
          sign(cookie, actId)
        }.compose {
          fetchSignInfo(cookie, actId)
        }.onSuccess {
          println("共签到 ${it.getJsonObject("data").getInteger("total_sign_day")} 天")
          msg.reply(it)
        }.onFailure {
          msg.fail(500, it.message)
        }
    }
  }

  private fun fetchUserInfo(cookie: String): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    client.get(443, "bbs-api-os.mihoyo.com", "/community/user/wapi/getUserFullInfo")
      .ssl(true)
      .putHeaders(HEADERS1)
      .putHeader("Cookie", cookie)
      .`as`(BodyCodec.jsonObject())
      .expect(ResponsePredicate.SC_OK)
      .send {
        if (it.succeeded()) {
          promise.complete(it.result().body())
        } else {
          promise.fail(it.cause())
        }
      }

    return promise.future()
  }

  private fun fetchSignInfo(cookie: String, actId: String): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    client.get(443, "hk4e-api-os.mihoyo.com", "/event/sol/info")
      .ssl(true)
      .putHeaders(HEADERS2)
      .putHeader("Cookie", cookie)
      .addQueryParam("lang", "zh-cn")
      .addQueryParam("act_id", actId)
      .`as`(BodyCodec.jsonObject())
      .expect(ResponsePredicate.SC_OK)
      .send {
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

    val signBody = JsonObject(
      """
    {
      "act_id": "$actId"
    }
    """
    )

    client.post(443, "hk4e-api-os.mihoyo.com", "/event/sol/sign")
      .ssl(true)
      .putHeaders(HEADERS2)
      .putHeader("Cookie", cookie)
      .addQueryParam("lang", "zh-cn")
      .`as`(BodyCodec.jsonObject())
      .expect(ResponsePredicate.SC_OK)
      .sendJson(signBody) {
        if (it.succeeded()) {
          promise.complete(it.result().body())
        } else {
          promise.fail(it.cause())
        }
      }

    return promise.future()
  }
}
