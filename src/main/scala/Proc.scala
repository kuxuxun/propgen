package propgen

object Proc{
  /** これと同じものがscala の標準ライブラリとかに多分ある、、 */
  def proc[A, B] (r :A)(f: A => B) = f(r)

  def unless[A] (c : Boolean)(f:Unit => A) = if (!c) f()
}

