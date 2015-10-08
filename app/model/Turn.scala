package model

/**
 * Created by greg.rubino on 10/7/15.
 */
case class Turn(gameId: String, playerIndex: Int, moves: List[Move], bananaCards: List[BananaCard])
