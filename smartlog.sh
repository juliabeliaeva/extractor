#!/bin/bash

filepath=$1
outfile=$2
revisions=$3
revision=HEAD

echo $filepath >> $outfile

while true
do
	git log --pretty=format:"%H" $revision -- $filepath >> $revisions
	echo "" >> $revisions
	revision=$(git log --pretty=format:"%H" $revision -- $filepath | tail -1)
	renameline=$(git show -M --follow --name-status $revision -- $filepath | tail -1)
	revision=$(git show -M --follow --name-status $revision -- $filepath | head -1 | awk '{print $2}')
	if echo $renameline | grep -oE "^R.*$" > /dev/null
	then
		filepath=$(echo $renameline | awk '{print $2}')
		echo $filepath >> $outfile
	else
		break
	fi
done