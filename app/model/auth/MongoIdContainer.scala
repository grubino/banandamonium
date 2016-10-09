package model.auth

import jp.t2v.lab.play2.auth.{AuthenticityToken, IdContainer}
import model.Player
import play.api.libs.json.Json
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.Await
import scala.concurrent.duration._
import reactivemongo.play.json._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by greg on 10/9/16.
  */
class MongoIdContainer(jsonCollection: JSONCollection) extends IdContainer[String] {
  override def startNewSession(userId: String, timeoutInSeconds: Int): AuthenticityToken = {
    Await.result(jsonCollection.find(Json.obj("name" -> userId)).one[Player].flatMap { playerMaybe =>
      val player = playerMaybe.getOrElse { throw new Exception("player not found") }
      val uuid = java.util.UUID.randomUUID.toString
      jsonCollection.insert(
        player.copy(gameTokens = Some(player.gameTokens.map(uuid :: _).getOrElse(List(uuid))))).map { _ => uuid }
    }, 30 seconds)
  }

  override def get(token: AuthenticityToken): Option[String] =
    Await.result(jsonCollection.find(
      Json.obj("tokens" ->
        Json.obj("$elemMatch" -> Json.obj("$eq" -> token)))).one[Player].map(_.map(_.name)), 30 seconds)

  override def remove(token: AuthenticityToken): Unit =
    Await.result(jsonCollection.update(
      Json.obj("tokens" ->
        Json.obj("$elemMatch" -> token)),
      Json.obj("tokens" -> Json.obj("$pull" -> Json.obj("$eq" -> token)))), 30 seconds)

  override def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int): Unit = {}
}
