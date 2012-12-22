propgen
=======

環境ごとのプロパティファイルを作成します。

## 概要
    1.エクセルファイルに環境ごと(development ,staging,production ..) のプロパティ値を書く
    2.オリジナルのpropertiesファイルのプロパティ値を(1)を元に変換して各環境用のpropertiesファイルを生成
    対応しているファイルエンコーディングはISO8859_1のみです。

## ビルド
    mvn assembly:assembly

## ヘルプ
    java -jar propgen-0.0.2-jar-with-dependencies.jar -h
