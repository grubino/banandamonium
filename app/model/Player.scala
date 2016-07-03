package model

import model.auth.Role
import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/2/15.
 */
case class Player(name: String, password: String, gameTokens: Option[List[String]])
object Player {
  implicit val playerF = Json.format[Player]
}