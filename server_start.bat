for /L %%i in (21,1,50) do start "server",%%i cmd /k call smartrun.bat sbft.benchmark.ThroughputLatencyServer %%i

exit