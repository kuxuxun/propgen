package propgen

import com.github.kuxuxun.scotch.excel.{ScWorkbook, ScSheet}
import com.github.kuxuxun.scotch.excel.cell.ScCell
import com.github.kuxuxun.scotch.excel.cell.ScCell.CellType
import com.github.kuxuxun.scotch.excel.area.ScPos
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.{FileInputStream,File,InputStreamReader,PrintWriter}
import java.util.Properties
import scala.collection.immutable.HashMap
import scala.io.Source
import Proc._
import WithFileSystem._

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

    unless(args.silent)( _ => println("Start generating ...."))

    rm(new File(conf.destDirPath))

    proc(EnviromentSetting.loadEnvSettings) { envSettings =>
      conf.srdDir.listFiles.foreach{ propFile =>
        if (propFile.isFile){

          unless(args.silent)(_ => println("Converting [" + propFile.getName + "] ===================="))

          envSettings.foreach{ case eachEnv =>
             eachEnv.convAndOutput(propFile)
          }
        }
      }
    }

    unless(args.silent){ _ =>
      println("Finished!!! ")
      println("Check => " + conf.destDirPath)
   }
}

  def showUsage = {
    var usage = "\nusage: java -jar propgen.jar [options]   \n"
    usage += "Options:\n"
    usage += " -D     システムプロパティーを変更します。\n"
    usage += "           [propgenのシステムプロパティ] \n"
    usage += "           destDir  出力ファイル格納ディレクトリ。デフォルトは./dest/\n"
    usage += "                    *** 注意: destDirに指定されたディレクトリは実行時に一度削除されます。***\n"
    usage += "           srcDir   入力ファイル格納ディレクトリ。デフォルトは./org/\n"
    usage += "           settingFilePath     環境ごとの設定ファイルパス\n"
    usage += "                               デフォルトはsetting/env.xls\n"
    usage += "           outputfile.encoding 出力ファイルエンコーディング。\n"
    usage += "                               デフォルトはシステムのエンコーディング\n"
    usage += "           inputfile.encoding  入力ファイルエンコーディング。\n"
    usage += "                               デフォルトはシステムのエンコーディング\n"
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
     if (value.isEmpty) line
     else if (value == orgValue) line
     else """(^.*=[\t|\s]*).*""".r.replaceAllIn(line, m => m.group(1) + value )
  }

  /** ファイルのプロパティ値の変換を行い、ファイルを出力します。*/
  def convAndOutput(orgPropFile:File)(implicit conf:Config, args:Args)  = {

      val destDir = genDirs(new File(formatDir(formatDir(conf.destDirPath) + this.name)))
      val destFile = new PrintWriter(formatDir(destDir.getAbsolutePath) + orgPropFile.getName, conf.outputFileEncoding)
      val propName = dropExtention(orgPropFile.getName)

      unless(args.silent)(_ => println("  - [" + name +"]  "))

      using(destFile) { out =>
        val orgFile =  Source.fromInputStream(new FileInputStream(orgPropFile) )(conf.inputFileEncoding)
        using(orgFile){ in =>
          in.getLines.zipWithIndex.foreach{ case (line, ind) => {
            val newLine = this.convertIfKeyDefined (propName, line)
            unless(args.silent){ _ =>
              println("     " + ( ind + 1) +": "+ (if(newLine == line) "Keep  : " + line else "Change: "  + line + " -> " + newLine)) }

            out.println(newLine)
          }}
        }
      }
    }
}

class Config() {

  val defaultProp = new Properties()
  defaultProp.load(getResource("genprop.properties"))

  ( "destDir":: "srcDir" :: "settingFilePath" :: Nil ).foreach{  key =>
    System.setProperty( key, defaultProp.getProperty(key))
  }

  ("outputfile.encoding" :: "inputfile.encoding" :: Nil).foreach{ key =>
    System.setProperty( key, System.getProperty("file.encoding"))
  }

  def outputFileEncoding = sysprop("outputfile.encoding")
  def inputFileEncoding = sysprop("inputfile.encoding")

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

