package model

import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/6/15.
 */
case class BananaCards(deck: List[BananaCard], active: List[BananaCard], discard: List[BananaCard])
