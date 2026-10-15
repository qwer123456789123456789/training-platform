<!-- src/views/login/LoginView.vue —— 登录 + 注册（真实接口版） -->
<template>
  <div style="width: 380px; margin: 15vh auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 2px 12px rgba(0,0,0,.06)">
    <h2 style="text-align: center; margin-bottom: 24px">课程实训成果管理平台</h2>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
      <el-form-item label="账号" prop="username">
        <el-input v-model="form.username" placeholder="请输入账号" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" @keyup.enter="handleLogin" />
      </el-form-item>
    </el-form>
    <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">登 录</el-button>
    <div style="text-align: center; margin-top: 12px; font-size: 13px">
      没有账号？<el-link type="primary" @click="openRegister">立即注册</el-link>
    </div>

    <el-dialog v-model="registerVisible" title="注册账号" width="420px">
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="registerForm.role" style="width: 100%">
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="handleRegister">注 册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, addUser } from '@/api/user'

const router = useRouter()

// ===== 登录 =====
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const data = await login(form)
      localStorage.setItem('token', data.token)
      localStorage.setItem('role', data.role)
      localStorage.setItem('realName', data.realName)
      if (data.id) localStorage.setItem('userId', data.id) // 需后端登录返回 id
      ElMessage.success('登录成功')
      router.push('/admin')
    } catch (e) {
      // 错误信息由 request.js 统一弹出
    } finally {
      loading.value = false
    }
  })
}

// ===== 注册 =====
const registerVisible = ref(false)
const registering = ref(false)
const registerFormRef = ref()
const registerForm = reactive({ username: '', realName: '', password: '', confirmPassword: '', role: 'student' })

const registerRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const openRegister = () => {
  Object.assign(registerForm, { username: '', realName: '', password: '', confirmPassword: '', role: 'student' })
  registerVisible.value = true
}

const handleRegister = () => {
  registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    registering.value = true
    try {
      await addUser({
        username: registerForm.username,
        realName: registerForm.realName,
        password: registerForm.password,
        role: registerForm.role
      })
      ElMessage.success('注册成功，请登录')
      registerVisible.value = false
    } catch (e) {
      // 错误信息由 request.js 统一弹出
    } finally {
      registering.value = false
    }
  })
}
</script>
