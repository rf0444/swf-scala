package jp.rf.swfsample

import scala.reflect.{ClassTag, classTag}

package object util {
  def safeCast[A: ClassTag](x: Any): Option[A] = {
    val cls = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    if (cls.isInstance(x)) Some(cls.cast(x)) else None
  }
}
