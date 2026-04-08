Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("C:\Users\user\.gradle\caches\modules-2\files-2.1\curse.maven\tacz-1028108-7745481\cb84817b83025fa93d380e87de655e57a607ced8\tacz-1028108-7745481.jar")
$zip.Entries | Where-Object { $_.FullName -like "*tags*items*.json" -or $_.FullName -like "*tacz_tags*attachments*.json" } | Select-Object -ExpandProperty FullName
$zip.Dispose()
