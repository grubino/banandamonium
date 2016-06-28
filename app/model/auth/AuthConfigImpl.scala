package model.auth

import jp.t2v.lab.play2.auth.{AuthConfig, AuthenticityToken, TokenAccessor}
import model.Player
import play.api.Environment
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, ResponseHeader, Result, Results}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import play.api.libs.json._
import Function._
/**
  * Created by greg.rubino on 6/27/16.
  */

case object UndefinedRole extends Throwable

trait Role
object Role {
  val roleR = (__).read[String].map { json =>
    json match {
      case "God" => God
      case "Mortal" => Mortal
      case _ => throw UndefinedRole
    }
  }
  val roleW: Writes[Role] = ((__).write[Role])(unlift(unapply))
  implicit val roleF = Format(roleR, roleW)

  def apply(roleName: String) = {
    roleName match {
      case "God" => God
      case "Mortal" => Mortal
      case _ => throw UndefinedRole
    }
  }
  def unapply(role: Role) = {
    role match {
      case God => Some("God")
      case Mortal => Some("Mortal")
      case _ => throw UndefinedRole
    }
  }
}

case object God extends Role
case object Mortal extends Role



trait BananaAuthConfig extends AuthConfig {
  val mongoApi: ReactiveMongoApi
  type Id = String
  type User = Player
  type Authority = User => Future[Boolean]
  val idTag: ClassTag[Id] = classTag[Id]
  val sessionTimeoutInSeconds: Int = 3600
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
    mongoApi.database.flatMap {
      db => db.collection[JSONCollection]("players").find(Json.obj("userName" -> id)).one[Player]
    }
  }
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
