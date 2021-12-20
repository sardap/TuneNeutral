import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { Row, Column, Hidden } from "vue-grid-responsive";

const app = createApp(App);

app.component("row", Row);
app.component("column", Column);
app.component("hidden", Hidden);

app.use(router).mount("#app");
