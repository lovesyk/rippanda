# rippanda : An amazing project
## Description
rippanda is an archiver tool for certain gallery websites using the panda-focused content system.

```
Usage: [-a=path] [-d=time] [-p=address] [-s=path] -u=url -c=cookies... <operationMode>
  <operationMode>        Operation mode: DOWNLOAD, UPDATE (default: DOWNLOAD)
  -a, --archive-dir=path Directory containing archived galleries (default: .)
  -c, --cookies=cookies  Log-in / perk cookies in key=value pairs separated by ;
  -d, --delay=time       Minimum delay between web request in ISO-8601 time format (default: 1S)
  -p, --proxy=address    SOCKS5 proxy to use for network requests and DNS resolution.
  -s, --success-dir=path Directory containing success files (default: .)
  -u, --url=url          Base URL to use for web requests or a more specific search URL if in download mode

Example: rippanda.jar --cookies "ipb_member_id=42; ipb_pass_hash=deadbeef" --success-dir "C:\Users\me\Downloads\success" --archive-dir "C:\Users\me\Downloads\archive" --url "https://somepandasite.org/?f_search=artbook" --proxy "127.0.0.1:1080" --delay 5S download
```

![Example Download](/screenshot.png?raw=true "Example Download")

## Building with Maven
```
mvn clean package
```

## Running with Java 11
```
java -jar rippanda.jar
```
