import scalaz._
import Scalaz._
object ScalazProblem {
  val x:Validation[String, Int] = 1.success
  val y:Validation[String, Int] = "wrong".fail

  /*start*/(x.liftFailNel |@| y.liftFailNel) {(a, b) => a + b}/*end*/
}
//({type λ[α] = Validation[NonEmptyList[String], α]})#λ[Int]