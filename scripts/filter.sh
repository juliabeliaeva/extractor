#!/bin/bash

shopt -s nullglob || exit

history=$1

git filter-branch --tree-filter '
#!/bin/bash

walk() {
	for file in $1/* $1/.??*
	do 
		if  ! grep -q -e "^$file" '"$history"'
		then
			#echo "removing $file"
			rm -rf $file
		elif [ -d $file ]
		then
			#echo "leaving $file"
			walk $file
		#else
			#echo "leaving $file"
		fi
	done
}

for file in * .??*
do
    if  ! grep -q -e "^$file" '"$history"'
    then
        #echo "removing $file"
        rm -rf $file
    elif [ -d $file ]
    then
        #echo "leaving $file"
        walk $file
    #else
        #echo "leaving $file"
    fi
done
' --prune-empty HEAD