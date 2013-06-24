#!/bin/bash

target=$1

for file in $(find $target -depth -name '*')
do
	echo $file
	./smartlog.sh $file history.txt revisions.txt
done