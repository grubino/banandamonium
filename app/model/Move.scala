package model

import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/3/15.
 */
case class Move(playerId: Int, layerIndex: Int, placeIndex: Int, monkeyCount: Int, distance: List[Int])
