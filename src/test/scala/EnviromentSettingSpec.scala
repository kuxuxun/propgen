import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import propgen.EnviromentSetting
import com.github.kuxuxun.scotch.excel.area.ScPos
import scala.collection.immutable.Map

@RunWith(classOf[JUnitRunner])
class EnviromentSettingSpec extends FlatSpec with ShouldMatchers{

  "EnviromentSetting " should "zip propname cells and key cells to tuple  " in{
    val xs = (new ScPos("A2") , "Here is A2"):: (new ScPos("A3"), "Here is A3") :: Nil
    val ys = (new ScPos("B2") , "Here is B2"):: (new ScPos("B3"), "Here is B3") :: Nil
    val zipped : List[(String,String,Int)] = EnviromentSetting.zipWithRowNo(xs, ys)


    zipped should have size(2)
    zipped(0)._1 should equal ("Here is A2")
    zipped(0)._2 should equal ("Here is B2")
    zipped(0)._3 should equal (1)

    zipped(1)._1 should equal ("Here is A3")
    zipped(1)._2 should equal ("Here is B3")
    zipped(1)._3 should equal (2)
  }

  it should "convert triple strings to map whose strucure is String -> String -> String " in{
  val l = ("p1","k1","v1-1") :: ("p1","k1","v1-2") :: ("p1","k2","v2-1") ::
  ("p2","k1","v1-1") :: Nil

  val m = EnviromentSetting.toPropNameToKeyToValueMap(l)
  m.keys should have size 2
  m.get("p1").get should have size 2 //k1, k2
  m.get("p1").get.get("k1").get should equal("v1-2") // v1-1 has overridden
  m.get("p1").get.get("k2").get should equal("v2-1") // v1-1 has overridden

  m.get("p2").get.get("k1").get should equal("v1-1")

  m.get("ddd") should equal(None)
  m.get("p2").get.get("dddd") should equal(None)
  }

  it should "convert value of properties key-value line" in {

    val convertMap =
      Map("p1" -> Map("k1" -> "これはp1.k1の値です",
                      "k2" -> "val of k2 of p1" ,
                      "k3" -> "") ) ++
      Map("p2" -> Map("k1" -> "val of k1 of p2") )

  val settingForLocal = EnviromentSetting("local",convertMap)

  settingForLocal.convertIfKeyDefined("p1","k1=aaaaa") should equal ("k1=これはp1.k1の値です")

  settingForLocal.convertIfKeyDefined("p1","k1  =  aaaaa") should equal ("k1  =  これはp1.k1の値です")

  settingForLocal.convertIfKeyDefined("p1","k3=aaaaa") should equal ("k3=aaaaa")
  settingForLocal.convertIfKeyDefined("p1","strangekey=aaaaa") should equal ("strangekey=aaaaa")

  settingForLocal.convertIfKeyDefined("p2","k1=aaaaa") should equal ("k1=val of k1 of p2")

  settingForLocal.convertIfKeyDefined("strangeprop","k1=aaaaa") should equal ("k1=aaaaa")

  }
}

