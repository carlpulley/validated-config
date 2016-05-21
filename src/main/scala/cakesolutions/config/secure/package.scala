// Copyright 2016 Carl Pulley

package cakesolutions.config

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros._

package object secure {
  @compileTimeOnly("Enable macro paradise to expand `CaseClassLike` macro annotations")
  class CaseClassLike extends StaticAnnotation {
    def CaseClassLike(annottees: Any*): Any = macro impl
  }

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    import Flag._

    annottees.map(_.tree).toList match {
      case q"""$mods class $className[..$tparams] $consMods (..$first)(...$rest) extends ..$parents { $self => ..$body }""" :: tail =>
        val applyMods = Modifiers()
        val applyDefn =
          q"""$applyMods def apply(..$first) = {
            new $className(name, timeout, http)
          }"""
        val unapplyDefn =
          q"""def unapply(obj: $className): Option[(String, FiniteDuration, SecureHttpConfig)] = {
            Some((obj.name, obj.timeout, obj.http))
          }"""
        val companionDefn = tail match {
          // if there is no exisiting companion then make one
          case Nil =>
            q"""object ${className.toTermName} {
              $applyDefn
              $unapplyDefn
            }"""
          case _ =>
            c.abort(c.enclosingPosition, "Annotated class should not have a companion object defined")
        }
        val hashCodeDefn =
          q"""override def hashCode = {
            List(name, timeout, http).map(_.hashCode).reduce[Int] { case (a, b) => 41 * a + b }
          }"""
        val equalsDefn =
          q"""override def equals(other: Any): Boolean = other match {
             case obj: $className =>
               obj.isInstanceOf[$className] &&
                 obj.name == name &&
                 obj.timeout == timeout &&
                 obj.http == http
             case _: Any =>
               false
           }"""
        val toStringDefn =
          q"""override def toString: String = {
            List(name, timeout, http).mkString(${className.toString}+"(", ", ", ")")
          }"""

        if (body.filter(_.isDef)) {
          c.abort(c.enclosingPosition, "Annotated class already has an `equals` method")
        }
        if (body.filter(_.isDef)) {
          c.abort(c.enclosingPosition, "Annotated class already has a `toString` method")
        }

        // TODO: check that $body doesn't already contain our additional methods and there's no clashes with introduced val's
        val result =
          q"""$mods class $className[..$tparams] $consMods (..$first)(...$rest) extends ..$parents {
            $self =>

            $toStringDefn
            $hashCodeDefn
            $equalsDefn

            ..$body
          }
          $companionDefn
          """

        c.Expr[Any](result)

      case _ =>
        c.abort(c.enclosingPosition, "Annotation `@CaseClassLike` can be used only with class declarations")
    }
  }
}
