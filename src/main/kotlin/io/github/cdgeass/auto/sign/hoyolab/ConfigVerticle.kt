package io.github.cdgeass.auto.sign.hoyolab

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject

/**
 * @author cdgeass
 * @since 2021-11-18
 */
class ConfigVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>?) {
    vertx.eventBus().consumer<String>("load.config") { msg ->
      loadConfig().onSuccess {
        msg.reply(it)
      }.onFailure {
        msg.fail(500, it.message)
      }
    }
  }

  private fun loadConfig(): Future<JsonObject> {
    val promise = Promise.promise<JsonObject>()

    vertx.fileSystem().readFile("./config.json") {
      if (it.succeeded()) {
        promise.complete(it.result().toJsonObject())
      } else {
        promise.fail(it.cause())
      }
    }

    return promise.future()
  }
}
