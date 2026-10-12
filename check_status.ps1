$client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 6379)
$stream = $client.GetStream()
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter($stream, $utf8NoBom)
$reader = New-Object System.IO.StreamReader($stream, $utf8NoBom)

$writer.WriteLine("AUTH lisiyu20060404")
$writer.Flush()
$reader.ReadLine()

$writer.WriteLine("GET trip:plan:P-20261012-002:status")
$writer.Flush()
$len1 = $reader.ReadLine()
$st = $reader.ReadLine()
Write-Output "STATUS_KEY: $st"

$writer.WriteLine("GET trip:plan:P-20261012-002:summary")
$writer.Flush()
$len2 = $reader.ReadLine()
$sm = $reader.ReadLine()
Write-Output "SUMMARY_KEY: $sm"

$client.Close()
