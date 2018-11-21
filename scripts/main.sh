#!/bin/bash

script=`realpath $0`
scriptdir=`dirname $script`
tmpdir=`realpath $PWD`/generated
mkdir -p "$tmpdir"

repository=$1
target=$2

pushd "$repository"

for file in $(find "$target" -depth -name '*')
do
	echo $file
	"$scriptdir/smartlog.sh" "$file" "$tmpdir/history.txt" "$tmpdir/revisions.txt"
done

popd

sort "$tmpdir/revisions.txt" | uniq > "$tmpdir/revisions.unique.txt"
./gradlew run --args="$repository $tmpdir/revisions.unique.txt $tmpdir/buildtree.sh"

pushd "$repository"

chmod +x "$tmpdir/buildtree.sh"
newhead=`"$tmpdir/buildtree.sh" | tail -1 | awk '{print $2}'`
git checkout $newhead
"$scriptdir/filter.sh" "$tmpdir/history.txt"

popd

