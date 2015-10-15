package model

/**
 * Created by greg.rubino on 10/9/15.
 */
case class Monkey(playerId: Int, monkeyId: Int) {
  override def equals(obj: scala.Any): Boolean = obj match {
    case that: Monkey => that.monkeyId == this.monkeyId
    case _ => false
  }
  override def hashCode(): Int = monkeyId.hashCode()
}
