<#
  用户与权限模块（后端1 / feature/user-auth）端到端自测脚本

  用法（先启动 Demo2Application，再运行本脚本）：
    pwsh -File api-test.ps1
    pwsh -File api-test.ps1 -BaseUrl http://localhost:8080 -AdminUser admin -AdminPassword 123456

  脚本会自己创建/禁用/删除一个临时学生账号（名字形如 t_auto_103000），不会动你已有的数据。
  退出码 0 = 全部通过，1 = 有失败项（可直接用于 CI 或汇报）。
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUser = 'admin',
    [string]$AdminPassword = '123456'
)

$script:pass = 0
$script:fail = 0

function Write-Step([string]$title) {
    Write-Host ''
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Assert-Equal([string]$name, $expected, $actual) {
    if ("$expected" -eq "$actual") {
        $script:pass++
        Write-Host ('  [PASS] ' + $name) -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host ('  [FAIL] ' + $name + '   期望=' + $expected + '  实际=' + $actual) -ForegroundColor Red
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )
    $headers = @{}
    if ($Token) { $headers['token'] = $Token }
    $request = @{
        Method      = $Method
        Uri         = "$BaseUrl$Path"
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec  = 15
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 5 -Compress
        # 用 UTF-8 字节发送，避免 Windows 下中文变成乱码
        $request['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-ApiRaw @request
}

<#
  响应统一按 UTF-8 解码后再转成对象。
  原因：后端返回的是标准 UTF-8 字节（Content-Type: application/json，JSON 本身规定就是 UTF-8，
  Spring 6+ 不再往响应头里塞 charset），浏览器/axios/Vue 客户端都没问题；
  但 Windows PowerShell 5.1 的 Invoke-RestMethod 在响应头没有 charset 时会按 ISO-8859-1 解码，
  中文就变成「è´¦å·æè¯¯」这种乱码，导致"提示是否中文正确"的断言误报失败。
  这里自己取原始字节按 UTF-8 解码，PowerShell 5.1 和 PowerShell 7 下都正确。
#>
function Invoke-ApiRaw {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [string]$ContentType,
        [int]$TimeoutSec = 15,
        $Body
    )
    $request = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        TimeoutSec  = $TimeoutSec
        UseBasicParsing = $true
    }
    if ($ContentType) { $request['ContentType'] = $ContentType }
    if ($null -ne $Body) { $request['Body'] = $Body }
    $response = Invoke-WebRequest @request
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

Write-Host "目标服务：$BaseUrl" -ForegroundColor Yellow
$stamp = Get-Date -Format 'HHmmss'
$stuUser = "t_auto_$stamp"

try {
    Write-Step '1. 管理员登录'
    $login = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $AdminUser; password = $AdminPassword }
    Assert-Equal '登录 code=200' 200 $login.code
    Assert-Equal '返回 token 非空' $true ([bool]$login.data.token)
    Assert-Equal '返回 role=admin' 'admin' $login.data.role
    Assert-Equal '返回 id（前端提交成果要用）' $true ($null -ne $login.data.id)
    Assert-Equal '响应里不含 password 字段' $false ($null -ne $login.data.password)
    $adminToken = $login.data.token
    $adminId = $login.data.id

    Write-Step '2. 未登录 / token 非法'
    $r = Invoke-Api -Method GET -Path '/api/user/page'
    Assert-Equal '不带 token 访问分页 -> 401' 401 $r.code
    $r = Invoke-Api -Method GET -Path '/api/user/page' -Token 'wrong.token.value'
    Assert-Equal '伪造 token -> 401' 401 $r.code
    $r = Invoke-ApiRaw -Method GET -Uri "$BaseUrl/api/user/page?current=1&size=1" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 15
    Assert-Equal 'Authorization: Bearer 方式同样可用' 200 $r.code

    Write-Step '3. 登录失败分支'
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $AdminUser; password = 'definitely-wrong' }
    Assert-Equal '密码错误 -> 401' 401 $r.code
    Assert-Equal '提示不暴露账号是否存在' '账号或密码错误' $r.msg
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = ''; password = '123456' }
    Assert-Equal '账号为空 -> 400（@Valid 生效）' 400 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = 'no_such_user_xyz'; password = '123456' }
    Assert-Equal '账号不存在 -> 401' 401 $r.code

    Write-Step '4. 分页查询'
    $r = Invoke-Api -Method GET -Path '/api/user/page?current=1&size=5' -Token $adminToken
    Assert-Equal '分页 code=200' 200 $r.code
    Assert-Equal 'data 含 records' $true ($null -ne $r.data.records)
    Assert-Equal 'data 含 total' $true ($null -ne $r.data.total)
    Assert-Equal 'size 回显=5' 5 $r.data.size
    $r = Invoke-Api -Method GET -Path '/api/user/page?current=0&size=500' -Token $adminToken
    Assert-Equal 'current=0 被修正为 1' 1 $r.data.current
    Assert-Equal 'size=500 被压到上限 100' 100 $r.data.size

    Write-Step '5. 新增用户与参数校验'
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = $stuUser; password = '123456'; realName = '自动化测试学生'; role = 'student' }
    Assert-Equal '新增学生 code=200' 200 $r.code
    Assert-Equal '返回新用户 id' $true ($null -ne $r.data)
    $stuId = $r.data

    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = $stuUser; password = '123456'; realName = '重复账号'; role = 'student' }
    Assert-Equal '账号重复 -> 400' 400 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = "t_bad_$stamp"; password = '123456'; realName = '非法角色'; role = 'superman' }
    Assert-Equal '角色非法 -> 400' 400 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = "t_pwd_$stamp"; password = '123'; realName = '短密码'; role = 'student' }
    Assert-Equal '密码过短 -> 400' 400 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = "t_cls_$stamp"; password = '123456'; realName = '班级不存在'; role = 'student'; classId = 999999 }
    Assert-Equal 'classId 不存在 -> 400' 400 $r.code

    Write-Step '6. 学生登录与越权访问'
    $stuLogin = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $stuUser; password = '123456' }
    Assert-Equal '学生登录 code=200' 200 $stuLogin.code
    Assert-Equal '学生登录返回 role=student' 'student' $stuLogin.data.role
    $stuToken = $stuLogin.data.token

    $r = Invoke-Api -Method GET -Path '/api/user/page' -Token $stuToken
    Assert-Equal '学生查用户列表 -> 403' 403 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/$stuId" -Token $stuToken
    Assert-Equal '学生查自己 -> 200' 200 $r.code
    Assert-Equal '详情里不含 password 字段' $false ($null -ne $r.data.password)
    $r = Invoke-Api -Method GET -Path "/api/user/$adminId" -Token $stuToken
    Assert-Equal '学生查别人 -> 403' 403 $r.code
    $r = Invoke-Api -Method DELETE -Path "/api/user/$adminId" -Token $stuToken
    Assert-Equal '学生删除管理员 -> 403' 403 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $stuToken -Body @{ username = "t_hack_$stamp"; password = '123456'; realName = '越权新增'; role = 'admin' }
    Assert-Equal '学生自己新增账号 -> 403' 403 $r.code

    Write-Step '7. 提权防护（重要）'
    $r = Invoke-Api -Method PUT -Path "/api/user/$stuId" -Token $stuToken -Body @{ username = $stuUser; realName = '张三'; role = 'admin' }
    Assert-Equal '学生把自己改成 admin -> 403' 403 $r.code
    $r = Invoke-Api -Method PUT -Path "/api/user/$stuId" -Token $stuToken -Body @{ username = $stuUser; realName = '张三' }
    Assert-Equal '学生改自己姓名 -> 200' 200 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/$stuId" -Token $adminToken
    Assert-Equal '姓名已更新为 张三' '张三' $r.data.realName
    Assert-Equal '角色仍然是 student' 'student' $r.data.role

    Write-Step '8. 禁用账号：旧 token 立即失效'
    $r = Invoke-Api -Method PUT -Path "/api/user/$stuId" -Token $adminToken -Body @{ username = $stuUser; realName = '张三'; role = 'student'; status = 0 }
    Assert-Equal '管理员禁用学生 -> 200' 200 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/$stuId" -Token $stuToken
    Assert-Equal '被禁用后旧 token -> 401' 401 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $stuUser; password = '123456' }
    Assert-Equal '被禁用后无法登录 -> 401' 401 $r.code
    $r = Invoke-Api -Method PUT -Path "/api/user/$stuId" -Token $adminToken -Body @{ username = $stuUser; realName = '张三'; role = 'student'; status = 1 }
    Assert-Equal '管理员恢复启用 -> 200' 200 $r.code

    Write-Step '9. 重置密码'
    $r = Invoke-Api -Method PUT -Path "/api/user/$stuId" -Token $adminToken -Body @{ username = $stuUser; password = 'abc123456' }
    Assert-Equal '管理员重置密码 -> 200' 200 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $stuUser; password = 'abc123456' }
    Assert-Equal '用新密码登录 -> 200' 200 $r.code
    $r = Invoke-Api -Method POST -Path '/api/user/login' -Body @{ username = $stuUser; password = '123456' }
    Assert-Equal '旧密码失效 -> 401' 401 $r.code

    Write-Step '10. 业务护栏'
    $r = Invoke-Api -Method DELETE -Path "/api/user/$adminId" -Token $adminToken
    Assert-Equal '管理员删除自己 -> 400' 400 $r.code
    $r = Invoke-Api -Method PUT -Path "/api/user/$adminId" -Token $adminToken -Body @{ username = $AdminUser; role = 'student' }
    Assert-Equal '把最后一个管理员降级为 student -> 400（若你库里有多个 admin，本条不适用）' 400 $r.code

    Write-Step '11. 逻辑删除与 token 失效'
    $r = Invoke-Api -Method DELETE -Path "/api/user/$stuId" -Token $adminToken
    Assert-Equal '逻辑删除学生 -> 200' 200 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/$stuId" -Token $adminToken
    Assert-Equal '查已删除用户 -> 400' 400 $r.code
    $r = Invoke-Api -Method DELETE -Path "/api/user/$stuId" -Token $adminToken
    Assert-Equal '重复删除 -> 400' 400 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/page?username=$stuUser" -Token $adminToken
    Assert-Equal '已删除用户不出现在列表' 0 $r.data.total
    $r = Invoke-Api -Method POST -Path '/api/user' -Token $adminToken -Body @{ username = $stuUser; password = '123456'; realName = '同名重建'; role = 'student' }
    Assert-Equal '同名账号被已删除用户占用 -> 400（唯一索引+逻辑删除的坑）' 400 $r.code
    $r = Invoke-Api -Method GET -Path "/api/user/page?username=t_auto_$stamp" -Token $adminToken
    Assert-Equal '模糊查询可用' 200 $r.code
}
catch {
    Write-Host ''
    Write-Host '请求失败，接口没通。请依次检查：' -ForegroundColor Red
    Write-Host '  1) 应用是否已启动（IDEA 运行 Demo2Application）'
    Write-Host '  2) MySQL80 服务是否在运行，training_platform 库是否已创建（先执行 create-database.sql）'
    Write-Host '  3) application.properties 里的 spring.datasource.password 是否改成了你自己的密码'
    Write-Host ('  原始错误：' + $_.Exception.Message) -ForegroundColor DarkGray
    exit 1
}

Write-Step '测试结果'
if ($script:fail -gt 0) {
    Write-Host ("通过 " + $script:pass + " 项，失败 " + $script:fail + " 项") -ForegroundColor Red
} else {
    Write-Host ("全部通过：" + $script:pass + " 项") -ForegroundColor Green
}
Write-Host ("本次使用的临时账号：" + $stuUser + "（已逻辑删除，唯一索引仍被占用，属预期）") -ForegroundColor DarkGray

if ($script:fail -gt 0) { exit 1 }
