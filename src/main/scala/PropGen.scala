package propgen

import com.github.kuxuxun.scotch.excel.{ScWorkbook, ScSheet}
import com.github.kuxuxun.scotch.excel.cell.ScCell
import com.github.kuxuxun.scotch.excel.cell.ScCell.CellType
import com.github.kuxuxun.scotch.excel.area.ScPos
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.{FileInputStream,File,InputStreamReader,PrintWriter}
import java.nio.charset.Charset
import java.util.Properties
import scala.collection.immutable.HashMap
import scala.io.Source
import Proc._
import WithFileSystem._
import org.apache.commons.lang3.StringEscapeUtils._

/**
 * エクセルで設定されたプロパティ値によって各環境用プロパティファイルを生成します。
 * */
object PropGen{
  implicit def conf = new Config()

  def main(argsArr:Array[String]) : Unit= {
    implicit val args = Args(List.fromArray(argsArr))

    if(args.showHelp){
      showUsage
      return
    }

    unless(args.silent)(  println("Start generating ...."))

    proc(EnviromentSetting.loadEnvSettings) { envSettings =>
      conf.srdDir.listFiles.foreach{ propFile =>
        if (propFile.isFile){

          unless(args.silent)(println("Converting [" + propFile.getName + "] ===================="))

          envSettings.foreach{ case eachEnv =>
             eachEnv.convAndOutput(propFile)
          }
        }
      }
    }

    unless(args.silent){
      println("Finished!!! ")
      println("Check => " + conf.destDirPath)
   }
}

  def showUsage = {
    var usage = "\nusage: java [sysprop] -jar propgen.jar [options]   \n"
    usage += "SysProp:\n"
    usage += " -D     システムプロパティーを変更します。\n"
    usage += "           [propgenのシステムプロパティ] \n"
    usage += "           destDir  出力ファイル格納ディレクトリ。デフォルトは./dest/\n"
    usage += "           srcDir   入力ファイル格納ディレクトリ。デフォルトは./org/\n"
    usage += "           settingFilePath     環境ごとの設定ファイルパス\n"
    usage += "                               デフォルトはsetting/env.xls\n"
    usage += "Options:\n"
    usage += " -s     実行結果を表示しません。\n"
    usage += " -h     このヘルプを表示します。\n"
    println(usage)
  }
}

case class Args(args: List[String]) {
  def showHelp = args.contains("-h")
  def silent = args.contains("-s")
  def isEmpty = args.isEmpty
}

/** Excelファイルのセルを逐次読み込みます
 * セルの形式が文字列または空白意外の場合例外をスルーします
 * */
class  CellCrawler(val sheet : ScSheet){

  /**  空白セルにあたるまでセルの水平方向に読み込みます */
  def crawlInRowUntilEmpty(ref : String) : List[(ScPos, String)] = {
      crawlUntilEmpty(new ScPos(ref) , (pos => pos.moveInRow(1) ))
  }

  /**  空白セルにあたるまでセルの垂直方向に読み込みます */
  def crawlInColUntilEmpty(ref : String) : List[(ScPos, String)] = {
    crawlUntilEmpty(new ScPos(ref) , (pos => pos.moveInCol(1) ))
  }

  def crawlUntilEmpty(pos:ScPos , moving: (ScPos => ScPos) ): List[(ScPos, String)] = {
    sheet.getCellAt(pos).getCellType() match{
      case x @ _ if(x == CellType.STRING || x == CellType.BLANK) => ""
      case _ => throw new IllegalStateException(pos.toString  + "は文字列でないといけません。")
    }

    sheet.getCellAt(pos).getTextValue match {
      case "" => Nil
      case v @ _  => ((pos, v) :: Nil) ++ crawlUntilEmpty(moving(pos), moving)
    }
  }
}

object EnviromentSetting{

    def zipWithRowNo(xs : List[(ScPos, String)], ys : List[(ScPos,String)] ) : List[(String, String, Int)] = {
        if (xs.length != ys.length)
          throw new IllegalStateException("設定ファイルを確認してください。A列のプロパティファイル名称かB列の キーの値が空白になっている可能性があります。 ")

        xs.zip(ys).foldLeft(Nil :List[(String,String, Int)] ){ (ag, zipped) =>
              if(zipped._1._1.getRow != zipped._2._1.getRow )
                  throw new IllegalStateException("property名とキーのzipに失敗しました。行番号がずれています" + zipped)

              (zipped._1._2 ,zipped._2._2, zipped._1._1.getRow ) :: ag
        }.reverse
    }

  def loadEnvSettings(implicit conf: Config) :List[EnviromentSetting]= {

    proc(new ScWorkbook(WorkbookFactory.create(new FileInputStream(conf.settingFile)))) { wb =>
      proc(wb.getSheetAt(0) ){ sheet =>
        proc(new CellCrawler(sheet)) { crawler =>
          val propNames = crawler.crawlInRowUntilEmpty("A4") // propNameの定義開始位置
          val keys = crawler.crawlInRowUntilEmpty("B4")
          val envDefinitions = crawler.crawlInColUntilEmpty("C3") // 環境ごとの設定情報ヘッダ

          envDefinitions.map{ case envDefinition => EnviromentSetting(envDefinition, zipWithRowNo(propNames , keys)  , sheet)}
          }
        }
      }
    }

  def apply(eachEnviromentHeader : (ScPos, String),
            propNameAndKeyAndRows : List[(String, String, Int)] ,
            sheet :ScSheet ) : EnviromentSetting= {

    EnviromentSetting(eachEnviromentHeader._2,
        toPropNameToKeyToValueMap(
          readKeysAndValues(eachEnviromentHeader._1, propNameAndKeyAndRows, sheet)))
  }

  /** (propName, key ,value)のリストをpropName -> (key -> value) のMapへ変換します。*/
  def toPropNameToKeyToValueMap(pkvs:List[(String, String, String)]) : Map [String, Map[String,String]] = {
     pkvs.foldLeft(Map[String, Map[String, String]]()){ case (m, (p, k, v)) =>
       m ++ m.get(p).map{mm => Map(p -> (mm ++ Map(k -> v)))}.getOrElse(Map( p -> Map(k -> v)))
     }
  }

  /** シートから環境ごとの設定値(value)を読み取り、propName, key とvalueのタプルのリストとして返却します。*/
  def readKeysAndValues(envHeaderPos: ScPos, propNameAndKeyAndRows : List[(String,String,Int)], sheet : ScSheet) : List[(String, String,String)]= {

    def readPropNameAndKeyToValDefinition (envDefHeaderPos: ScPos, propNameAndKeyAndRow : (String,String,Int) , sheet:ScSheet) :(String, String,String) = {
      propNameAndKeyAndRow match  { case (propName, key, row) =>
        (propName, key, sheet.getCellAt(new ScPos(row, envDefHeaderPos.getCol)).getTextValue)
      }
    }

    propNameAndKeyAndRows match {
      case Nil => Nil
      case x :: Nil => readPropNameAndKeyToValDefinition(envHeaderPos,x,sheet) match { case (p, k,v) => (p, k,v) :: Nil}
      case x :: xs => readPropNameAndKeyToValDefinition(envHeaderPos, x, sheet) :: readKeysAndValues(envHeaderPos,xs,sheet)
      }
  }

}

/** 環境ごと(develop,staging,productin etc)の設定情報を表現します */
case class EnviromentSetting(name :String ,keyToValsForPropname :Map[String, Map[String,String] ]){

  def convertIfKeyDefined(propName:String, line:String) =  {
     def keyToValTuple(a : Array[String]) = if(a.length >= 2 ) (a(0), a(1)) else (a(0), "")

     val (key, orgValue) = keyToValTuple(line.split("=").map(_.trim))
     val value = keyToValsForPropname.get(propName).map(kv => kv.get(key)) .getOrElse(None) .getOrElse(orgValue)
     if (value.length == 0) line
     else if (value == orgValue) line
     else """(^.*=[\t|\s]*).*""".r.replaceAllIn(line, m => m.group(1) + UnicodeConverter.unicodeEscape(value))
  }

  /** ファイルのプロパティ値の変換を行い、ファイルを出力します。*/
  def convAndOutput(orgPropFile:File)(implicit conf:Config, args:Args)  = {

      val destDir = genDirs(new File(formatDir(formatDir(conf.destDirPath) + this.name)))
      val destDirPath = formatDir(destDir.getAbsolutePath) + orgPropFile.getName

      rm(new File(destDirPath))

      val destFile = new PrintWriter(destDirPath, "ISO8859_1")
      val propName = dropExtention(orgPropFile.getName)

      unless(args.silent)( println("  - [" + name +"]  "))
      using(destFile) { out =>
        val orgFile =  Source.fromInputStream(new FileInputStream(orgPropFile) )("ISO8859_1")
        using(orgFile){ in =>
          in.getLines.zipWithIndex.foreach{ case (line, ind) => {
            val newLine = this.convertIfKeyDefined (propName, line)
            unless(args.silent){
              println("     " + ( ind + 1) +": "+ unescapeJava(if(newLine == line) "Keep  : " + line else "Change: "  + line + " -> " + newLine)) }

            out.println(newLine)
          }}
        }
      }
    }

}

class Config() {

  val defaultProp = new Properties()
  defaultProp.load(getResource("genprop.properties"))

  ("destDir":: "srcDir" :: "settingFilePath" :: Nil ).foreach{  key =>
    if(System.getProperty(key) == null) System.setProperty( key, defaultProp.getProperty(key))
  }


  def destDirPath : String = sysprop("destDir")

  def srdDir = getExistingFile(srcDirPath, "元ファイル格納フォルダ")
  def srcDirPath : String = sysprop("srcDir")

  def settingFile  = getExistingFile(settingFilePath, "設定ファイル")
  def settingFilePath  = sysprop("settingFilePath")

  def sysprop(key :String ) = System.getProperty(key)

  def getExistingFile(path:String, msg: String) = {
    proc (new File(path)) {file =>
      if(!file.exists) throw new IllegalStateException(msg + " :[" + file.getAbsolutePath + "]は存在しません。")
      file
    }
  }
}

object UnicodeConverter{
  /**
   * 文字列をユニコードエスケープします。
   * <p>
   * ISO8859_1でエンコードできない文字列をユニコードエスケープ(udddd形式)して返却します
   * このメソッドが返却する文字列をファイルに書き込む事を想定しているため
   * 先頭には２つのバックスラッシュが付加されます(そのまま書き込むとバックスラッシュが１つになります。
   */
  def unicodeEscape(s: String) : String = {
    proc (Charset.forName("ISO8859_1").newEncoder){ asciiDetector =>
      s.toCharArray.foldLeft(""){ (encoded, eachChar) =>
        if(asciiDetector.canEncode(eachChar))
          encoded + eachChar
        else
          encoded + "\\\\u" + escapeChar(eachChar)
        }
    }
  }

  /** convert\udddd style */
  def escapeChar(c : Char) : String = {
    // 先頭0埋めのため0x10000との論理和をとる
    Integer.toHexString(0x10000 | c).substring(1).toUpperCase
  }
}


