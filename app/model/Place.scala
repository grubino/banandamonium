package model

import play.api.libs.json.Json

/**
 * Created by greg.rubino on 10/2/15.
 */
case class Place(monkeys: List[Monkey], slideUp: Option[Int], slideDown: Option[Int]) {

  def removeMonkeys(monkeysToRemove: List[Monkey]): Place = {
    Place(monkeys.filter(m => !monkeysToRemove.contains(m)), slideUp, slideDown)
  }

  def addMonkeys(monkeysToAdd: List[Monkey]): Place = {
    Place(monkeys ++ monkeysToAdd, slideUp, slideDown)
  }

  def getSlideUp: Int = {
    slideUp match {
      case Some(x) => x
      case None => -1
    }
  }
  def getSlideDown: Int = {
    slideDown match {
      case Some(x) => x
      case None => -1
    }
  }
}
