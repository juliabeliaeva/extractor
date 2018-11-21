#!/bin/bash

if [ "$#" -ne 2 ]
then
	echo "Usage: $0 <REPOSITORY> <PATH IN REPOSITORY>" >&2
	exit 1
fi

if ! [ -e "$1" ] || ! [ -d "$1" ]
then
	echo "$1 is not found or is not a directory" >&2
	exit 1
fi

if ! [ -e "$1/$2" ]
then
	echo "$2 is not found in repository $1" >&2
	exit 1
fi

script=`realpath $0`
scriptdir=`dirname $script`
tmpdir=`realpath $PWD`/generated
mkdir -p "$tmpdir"

repository="$1"
target="$2"

pushd "$repository"

if ! [ -e ".git" ]
then
	echo "$1 is not a git repository" >&2
	exit 1
fi

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

