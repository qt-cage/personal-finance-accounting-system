<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('nav.changePassword')"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item :label="$t('nav.oldPassword')" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item :label="$t('nav.newPassword')" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item :label="$t('nav.confirmPassword')" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ $t('nav.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="submit">{{ $t('nav.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { changePassword } from '../api/user'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()
const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const rules = computed(() => ({
  oldPassword: [{ required: true, message: t('validation.oldPasswordRequired'), trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, max: 20, message: t('validation.passwordLength'), trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: t('validation.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) callback(new Error(t('validation.passwordMismatch')))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}))

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success(t('nav.pwdSuccess'))
    emit('update:modelValue', false)
  } catch (error) {
    // 全局响应拦截器负责展示错误。
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}
</script>
