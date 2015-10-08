package model

import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/7/15.
 */
case class DiceRolls(gameId: String, turnIndex: Int, diceRolls: List[Int])
