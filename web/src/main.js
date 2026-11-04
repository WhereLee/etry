import { createApp } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CircleCheck,
  Clock,
  Close,
  Collection,
  CopyDocument,
  DataLine,
  Document,
  EditPen,
  FolderOpened,
  House,
  Key,
  Link,
  Lock,
  Menu,
  Plus,
  Reading,
  Refresh,
  Search,
  SwitchButton,
  Tickets,
  UploadFilled,
  User,
  UserFilled,
  VideoCamera,
  View,
  Warning
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-overlay.css'
import './style.css'

const icons = {
  ArrowLeft,
  ArrowRight,
  Check,
  CircleCheck,
  Clock,
  Close,
  Collection,
  CopyDocument,
  DataLine,
  Document,
  EditPen,
  FolderOpened,
  House,
  Key,
  Link,
  Lock,
  Menu,
  Plus,
  Reading,
  Refresh,
  Search,
  SwitchButton,
  Tickets,
  UploadFilled,
  User,
  UserFilled,
  VideoCamera,
  View,
  Warning
}

const app = createApp(App)
Object.entries(icons).forEach(([name, component]) => app.component(name, component))
app.use(router)
app.mount('#app')
