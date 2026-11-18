# Phát hiện cộng đồng bằng LPA trên Hadoop MapReduce

Bài môn Xử lý song song. Chương trình cập nhật nhãn đồng bộ trên đồ thị vô hướng. Mỗi vòng là một job MapReduce. Bản Java tuần tự dùng cùng quy tắc để đối chiếu.

- `src/lpa/` chứa Mapper, Reducer và Driver.
- `sequential/src/` chứa bản tuần tự.
- `Generator.cpp` và `data/` chứa bộ sinh và dữ liệu thử.
- `results/graph10/` chứa kết quả MapReduce mẫu đã chạy.

## Chạy thử bằng PowerShell trên Windows

Dùng JDK 8, Hadoop 2.6.0, đặt JAVA_HOME và thêm Java, Hadoop vào PATH. Khởi động HDFS và YARN. Chạy tại thư mục chứa README.

```powershell
.\build.bat
.\sequential\build.cmd
$tag = Get-Date -Format yyyyMMdd_HHmmss
hdfs dfs -mkdir -p /lpa/demo_input
hdfs dfs -put .\data\graph10.txt /lpa/demo_input/graph10_$tag.txt
hadoop jar .\LPA_Community.jar /lpa/demo_input/graph10_$tag.txt /lpa/demo_$tag 20 2
hdfs dfs -cat /lpa/demo_$tag/metrics.csv
hadoop jar .\sequential\LPA_Sequential.jar .\data\graph10.txt .\seq_$tag 20
```

Với đồ thị mẫu, số đỉnh đổi nhãn qua ba vòng là 10, 2, 0. Hai cộng đồng cuối gồm các đỉnh 1–5 và 6–10.

## So sánh hai bản trên 1.000, 5.000, 10.000 và 20.000 đỉnh

Mở PowerShell tại thư mục chứa README, khởi động HDFS và YARN rồi chạy `./build.bat` và `./sequential/build.cmd` một lần. Dán khối sau để chạy mỗi bản một lần cho từng kích thước. Để chỉ chạy một bộ, ví dụ 5.000 đỉnh, thay danh sách ở dòng đầu bằng `@(5000)`.

```powershell
$sizes = @(1000, 5000, 10000, 20000)
$tag = Get-Date -Format yyyyMMdd_HHmmss_fff
$root = "/lpa/compare_$tag"
hdfs dfs -mkdir -p "$root/input"
if ($LASTEXITCODE -ne 0) { throw "Khong tao duoc thu muc HDFS" }
foreach ($n in $sizes) {
    $file = if ($n -eq 1000) { "generated1000.txt" } else { "graph$n.txt" }
    $local = (Resolve-Path "data/$file" -ErrorAction Stop).Path
    $uri = ([uri]$local).AbsoluteUri
    $seq = "./seq_${tag}_$n"
    $mr = "$root/mr_$n"
    hdfs dfs -put $uri "$root/input/$file"
    if ($LASTEXITCODE -ne 0) { throw "Loi tai du lieu $n len HDFS" }
    hadoop jar ./sequential/LPA_Sequential.jar $local $seq 20
    if ($LASTEXITCODE -ne 0) { throw "Loi ban tuan tu $n" }
    hadoop jar ./LPA_Community.jar "$root/input/$file" $mr 20 2
    if ($LASTEXITCODE -ne 0) { throw "Loi ban Hadoop $n" }
    $s = Import-Csv "$seq/metrics.csv" | Select-Object -Last 1
    $rows = hdfs dfs -cat "$mr/metrics.csv"
    if ($LASTEXITCODE -ne 0) { throw "Loi doc metrics Hadoop $n" }
    $m = $rows | ConvertFrom-Csv | Select-Object -Last 1
    Write-Output "N=$n | Tuan tu: $($s.iteration) vong, $($s.total_seconds) s | Hadoop: $($m.iteration) vong, $($m.total_seconds) s"
}
```

Trong mỗi thư mục kết quả, `metrics.csv` ghi diễn biến từng vòng và `communities.tsv` ghi thành viên mỗi cộng đồng. Kết quả tuần tự nằm trên máy, kết quả Hadoop nằm trên HDFS. Để đo ba lần, chạy lại toàn bộ khối thêm hai lần.
