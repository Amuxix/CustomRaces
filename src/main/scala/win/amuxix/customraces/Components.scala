package win.amuxix.customraces

import net.kyori.adventure.text.{Component, TextComponent}
import net.kyori.adventure.text.format.TextColor

object Components:
  opaque type RichText = Iterable[Component]

  given Conversion[RichText, Component] =
    _.reduceLeft((joined, component) => joined.append(component))

  object RichText:
    def apply(text: String): RichText                            = List(Component.text(text))
    def justified(text: String, wordsPerLine: Int = 7): RichText =
      text
        .replaceAll("(\r\n|\r|\n)", "")
        .split(" ")
        .grouped(wordsPerLine)
        .map(words => Component.text(words.mkString(" ")))
        .toList

    lazy val empty: RichText     = List.empty
    lazy val emptyLine: RichText = List(Component.empty)

  extension (richText: RichText)
    infix def +(other: RichText): RichText      = richText ++ other
    infix def append(other: RichText): RichText =
      assert(richText.size == 1, "Append failed!")
      List(richText.head.append(other.headOption.getOrElse(Component.empty)))

    infix def *(x: Int): RichText         = List.fill(x)(richText).flatten
    def color(color: Int): RichText       = richText.map(_.colorIfAbsent(TextColor.color(color)))
    def asComponents: Iterable[Component] = richText

  extension (richText: Iterable[RichText]) def join: RichText = richText.flatten
