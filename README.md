# rippanda
## Description
rippanda is an archiver tool for certain gallery websites using the panda-focused content system.

```
Usage: [-a=path] [-d=time] [-p=address] [-s=path] [-e=element] -u=url -c=cookies... <operationMode>
  <operationMode>              Operation mode: DOWNLOAD, UPDATE (default: DOWNLOAD)
  -a, --archive-dir=path       Directory containing archived galleries (default: .)
  -c, --cookies=cookies        Log-in / perk cookies in key=value pairs separated by ;
  -d, --delay=time             Minimum delay between web request in ISO-8601 time format (default: 5S)
  -i, --update-interval=period Minimum interval when deciding whether to update a gallery in ISO-8601 period format (default: 30D)
  -e, --skip=element           Elements to skip during archival process (metadata, page, thumbnail, torrent, zip)
  -p, --proxy=address          SOCKS5 proxy to use for network requests and DNS resolution.
  -s, --success-dir=path       Directory containing success files (default: .)
  -u, --url=url                Base URL to use for web requests or a more specific search URL if in download mode

Example: rippanda.jar --cookies "ipb_member_id=42; ipb_pass_hash=deadbeef" --success-dir "C:\Users\me\Downloads\success" --archive-dir "C:\Users\me\Downloads\archive" --url "https://somepandasite.org/?f_search=artbook" --proxy "127.0.0.1:1080" --delay 4S download
```

![Example Download](/screenshot.png?raw=true "Example Download")

## Archivation
Currently the following elements will be downloaded and updated:
- untouched ZIP file containing all gallery images
- API metadata in JSON format
- initial page when opening the gallery containing some data like user comments not available through the API
- a high-quality thumbnail
- all torrent files associated with the gallery or previous versions

The update logic behaves as following:
1. A gallery will not be updated if the directory it resides in has been changed within the update inverval.
2. API metadata and the web page will always be updated if the whole gallery is not to be excluded by the above rule.
3. Torrent files will be updated / removed if they do not match the API files by comparing their file size and timestamps.
4. ZIP file and thumbnail will only be updated if their files are missing.

On errors the tool will retry a few times after waiting a bit but cancel the process if it deems the servers to be down or for the user to be banned. It will make sure the non-temporary success file only contains fully downloaded / updated galleries.

## GP / Credits Usage
The tool will not harvest images one-by-one, it will use the official way of purchasing the galleries requested by GP / credits. It's in the responsibility of the user to make sure there is enough currency available to purchase all requested galleries and stop the tool if those are close to run out.

While some more optimizations are possible, the tool will minimize the amount of data requested from the servers and hence should not come anywhere close to reaching Image Limits by itself even in the case of excessive archiving.

## Success Files
The so-called success files are simple text files recording the progress of your archivation efforts. The tool will create a temporary success file containing all IDs of galleries you have started downloading and transfer the ID to a non-temporary success file once the archivation is completed.

There are two purposes to those files:
- If you share the success directory with other users, you can (mostly) make sure the same gallery is only archived once for the whole group.
- This tremendously increases the startup speed of the tool since there is no need to read a possibly huge directory of galleries to find out which can safely be skipped.

## Delay
Make sure to set the delay accordingly to your needs. The default delay of 5 seconds should be fine for most use cases and prevent you from being banned by the web servers even in the case of excessive downloading / updating.

## Building with Maven
```
mvn clean package
```

## Running with Java 11
```
java -jar rippanda.jar
```
