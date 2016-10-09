package model.auth

import jp.t2v.lab.play2.auth.{AsyncIdContainer, AuthConfig, AuthenticityToken, TokenAccessor}
import model.Player
import play.api.Environment
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, ResponseHeader, Result, Results}
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import play.api.libs.json._
import reactivemongo.play.json.collection.JSONCollection
/**
  * Created by greg.rubino on 6/27/16.
  */

case object UndefinedRole extends Throwable

sealed trait Role
object Role {
  implicit val roleF = RoleFormatter
}
case object God extends Role
case object Mortal extends Role

object RoleFormatter extends Format[Role] {
  override def reads(json: JsValue): JsResult[Role] = json match {
    case JsString("God") => JsSuccess(God, __)
    case JsString("Mortal") => JsSuccess(Mortal, __)
    case _ => JsError("undefined role")
  }

  override def writes(o: Role): JsValue = o match {
    case God => JsString("God")
    case Mortal => JsString("Mortal")
  }
}



trait BananaAuthConfig extends AuthConfig {
  val mongoApi: ReactiveMongoApi
  type Id = String
  type User = Player
  type Authority = User => Future[Boolean]
  val idTag: ClassTag[Id] = classTag[Id]
  val sessionTimeoutInSeconds: Int = 3600
  val playerCollection: JSONCollection
  override lazy val idContainer = AsyncIdContainer(new MongoIdContainer(playerCollection))
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]]
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result]
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result]
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result]
  override def authorizationFailed(request: RequestHeader,
                                   user: User,
                                   authority: Option[Authority])
                                  (implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Results.Forbidden("no permission"))
  }
  def authorize(user: User,
                authority: Authority)
               (implicit ctx: ExecutionContext): Future[Boolean] = authority(user)

  override lazy val tokenAccessor = new TokenAccessor {
    override def put(token: AuthenticityToken)(result: Result)(implicit request: RequestHeader): Result = {
      result.withHeaders(("Authorization" -> token))
    }

    override def delete(result: Result)(implicit request: RequestHeader): Result = {
      result.copy(
        header = result.header.copy(
          headers = result.header.headers.filter { case (a, b) => a != "Authorization" }))
    }

    override def extract(request: RequestHeader): Option[AuthenticityToken] = {
      request.headers.get("Authorization")
    }

  }
}
