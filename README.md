# rippanda
## Description
rippanda is an archiver tool for certain gallery websites using the panda-focused content system.

```
Usage: rippanda [-tv] -c=cookies [-d=time] [-i=interval] [-p=host:port] [-s=path] -u=url [-a=path]... [-e=element]... mode
      mode                         Operation mode: download, update, cleanup
                                     Default: download
  -c, --cookies=cookies            Log-in / perk cookies in key=value pairs separated by ;
  -p, --proxy=host:port            SOCKS5 proxy to use for network requests and DNS resolution.
  -u, --url=url                    Base URL to use for web requests or a more specific search URL if in download mode
  -d, --delay=time                 Minimum delay between web request in ISO-8601 time format
                                     Default: 15S
  -i, --update-interval=interval   Update interval when deciding whether to update a gallery as ISO-8601 periods in format
                                     minThreshold=minDuration-maxThreshold=maxDuration
                                     Default: 0D=7D-365D=90D
  -a, --archive-dir=path           Directories containing archived galleries (first occurence denotes writable primary path)
                                     Default: .
  -s, --success-dir=path           Directory containing success files
                                     Default: .
  -e, --skip=element               Specify multiple times to skip elements during archival process. (metadata, page, imagelist, expungelog, thumbnail, torrent, zip)
  -t, --catchup                    Enables catch-up download mode to stop processing once a fully archived page has been encountered.
                                     Default: false
  -v, --verbose                    Specify up to 7 times to override logging verbosity (4 times by default)

Example download: rippanda.jar --cookies "ipb_member_id=42; ipb_pass_hash=deadbeef" --success-dir "C:\Users\me\Downloads\success" --archive-dir "C:\Users\me\Downloads\archive" --url "https://somepandasite.org/?f_search=artbook" --proxy "127.0.0.1:1080" --delay 5S download
Example update: rippanda.jar --cookies "ipb_member_id=42; ipb_pass_hash=deadbeef" --success-dir "C:\Users\me\Downloads\success" --archive-dir "C:\Users\me\Downloads\archive" --url "https://somepandasite.org" --skip torrent --skip imagelist --update-interval 0D=30D-0D=30D update
Example cleanup: rippanda.jar --cookies "ipb_member_id=42; ipb_pass_hash=deadbeef" --success-dir "/home/me/Downloads/success" --archive-dir "/home/me/Downloads/archive" --archive-dir "/home/someoneElse/Downloads/archive" --url "https://somepandasite.org" -vvvvv update
```

![Example Download](/screenshot.png?raw=true "Example Download")

## Download / update
Currently the following elements will be downloaded and updated:
- untouched ZIP file containing all gallery images
- API metadata in JSON format
- initial page when opening the gallery containing some data like user comments not available through the API
- the image list of the multi-page viewer page containing individual image hashes and file sizes in JSON format (should be disabled if user has no access to MPV)
- the expunge log page in case the gallery is marked as being expunged
- a high-quality thumbnail
- all torrent files associated with the gallery or previous versions

The update logic behaves as following:
1. API metadata and the web page will be updated if they have not been changed within the interpolated (min / max) duration specified by the update interval which is calculated based on the timestamp (min / max thresholds) the gallery was posted at.
2. Torrent files will be updated / removed if they do not match the API files by comparing their file size and timestamps.
3. ZIP file, thumbnail, image list and the expunge log (if expunged) will only be updated if their files are missing.

If the gallery is deemed to no longer be available due to copyright claims, only the API metadata will be updated.
On errors the tool will retry a few times after waiting a bit but cancel the process if it deems the servers to be down or for the user to be banned. It will make sure the non-temporary success file only contains fully downloaded / updated galleries.

## Cleanup
Cleanup mode will check all specified archive directories for galleries which have been superseeded by having a newer child gallery and remove them. The space savings will be shown after the process finishes.

## GP / Credits Usage
The tool will not harvest images one-by-one, it will use the official way of purchasing the galleries requested by GP / credits. It's in the responsibility of the user to make sure there is enough currency available to purchase all requested galleries and stop the tool if those are close to run out.

While some more optimizations are possible, the tool will minimize the amount of data requested from the servers and hence should not come anywhere close to reaching Image Limits by itself even in the case of excessive archiving.

## Success Files
The so-called success files are simple text files recording the progress of your archivation efforts. The tool will create a temporary success file containing all IDs of galleries you have started downloading and transfer the ID to a non-temporary success file once the archivation is completed.

There are two purposes to those files:
- If you share the success directory with other users, you can (mostly) make sure the same gallery is only archived once for the whole group.
- This tremendously increases the startup speed of the tool since there is no need to read a possibly huge directory of galleries to find out which can safely be skipped.

## Delay
Make sure to set the delay accordingly to your needs. The default delay of 15 seconds should be fine for most use cases and prevent you from being banned by the web servers even in the case of excessive downloading / updating.

## Building with Maven
```
mvn clean package
```

## Running with Java 17
```
java -jar rippanda.jar
```
