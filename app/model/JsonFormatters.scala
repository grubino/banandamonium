package model

import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/7/15.
 */
object JsonFormatters {
  implicit val moveFormat = Json.format[Move]
  implicit val bananaCardFormat = Json.format[BananaCard]
  implicit val bananaCardsFormat = Json.format[BananaCards]
  implicit val placeFormat = Json.format[Place]
  implicit val boardFormat = Json.format[Board]
  implicit val diceRollsFormat = Json.format[DiceRolls]
  implicit val turnFormat = Json.format[Turn]
}
