package propgen
import java.io.File

/** ファイル操作系のヘルパーモジュール */
object WithFileSystem{

  def formatDir(rawPath:String) = if(rawPath.endsWith("/")) rawPath else rawPath + "/"

  def rm(file:File){
    if (file.exists){
      if(file.isDirectory) file.listFiles.foreach{rm}
      file.delete
    }
  }

  def regenDirs(f:File) : File= {
    rm(f)
    genDirs(f)
  }

  def genDirs(f:File) : File= {
    if(!f.exists) f.mkdirs
    f
  }

  def using[A <% { def close():Unit}, B ] (res : A)( f : (A => B) )= {
    try{
      f(res)
      }finally {
        res.close()
    }
      }

  def getResource(name:String) = {
      getClass().getClassLoader().getResourceAsStream(name)
  }
  def dropExtention(fileName :String) = {
     fileName.replaceAll("(\\.[^\\.]*$)", "")
  }
}
