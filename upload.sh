#!/bin/bash
#
set -e

GET_UPLOAD_URL="/get-upload-url"
DEFAULT_URL=http://assets.fredsa.appspot.com/


curlflags=""
if [ "$1" = "-v" ]
then
	curlflags="$curlflags $1"
	shift
fi

url="$DEFAULT_URL"
if [ "$1" = "-url" ]
then
  shift
  url=$1
  shift
fi

if [ $# -lt 1 -o "$1" = "-?" ]
then
  [ "$url" != "$DEFAULT_URL" ] && urlargs="-url $url " || urlargs=""
  echo "App Engine asset uploader."
  echo ""
  echo "Usage:"
  echo "Manually upload one or more files:"
  echo "  $0 [-v] [-url <url>] <file1> [file2] ..."
  echo ""
  echo "Upload a moderate number of files, which all fit on a single command line:"
  echo "  find . -type f -exec $urlargs$0 {} \\;"
  echo ""
  echo "Upload a very large number of files one by one:"
  echo "  find . -type f | xargs $0 $urlargs"
  exit 1
fi

while [ $# -gt 0 ]
do
  file=$1
  shift
  echo "FILE: $file"

  # determine MIME Type
  mime_type=$(file --brief --mime-type $file)
  echo "- MIME Type: $mime_type"

  # request blobstore upload URL
  upload_url=$(curl -s -f $url$GET_UPLOAD_URL)

  # fix for devappserver lacking scheme/host/port
  upload_url=${url}${upload_url#${url}}

  echo "- Upload URL: $upload_url"

  basename=$(basename $file)
  echo "- basename: $basename"

  # upload content
  curl $curlflags -f -L -F "file=@$file;filename=$basename;type=$mime_type" $upload_url
done
