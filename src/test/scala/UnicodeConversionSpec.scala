import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import propgen.EnviromentSetting
import com.github.kuxuxun.scotch.excel.area.ScPos
import scala.collection.immutable.Map
import org.apache.commons.lang3.StringEscapeUtils._
import org.apache.commons.lang3.StringUtils._
import propgen._

@RunWith(classOf[JUnitRunner])
class UnicodeConversionSpec extends FlatSpec with ShouldMatchers{

  "UnicodeConverter" should "convert multi string to unicode escape sequence" in{

    UnicodeConverter.unicodeEscape("âäHello!öü") should equal("âäHello!öü")

    val s1 = "あいうえお!"
    UnicodeConverter.unicodeEscape(s1) should   equal("\\\\u3042\\\\u3044\\\\u3046\\\\u3048\\\\u304A!")

    unescapeJava( replace(UnicodeConverter.unicodeEscape(s1),"\\\\","\\")) should equal(s1)


  }

}

