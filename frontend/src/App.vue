<template>
  <div class="vld-parent">
    <loading
      v-model:active="loading"
      :can-cancel="false"
      :is-full-page="true"
      blur="3px"
    />
    <div v-if="authenticated">
      <div id="nav">
        <router-link to="/">Create Playlist</router-link> |
        <router-link to="/playlists">Playlists</router-link> |
        <router-link to="/my_data">My Data</router-link>
      </div>
      <router-view />
    </div>
    <div v-else>
      <h1>Tune Neutral</h1>
      <div class="center">
        <a href="auth" class="button get-started">Get started</a>
      </div>
    </div>
  </div>
  <div class="footer">
    <Logout v-if="authenticated" />
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import Loading from "vue-loading-overlay";
import "vue-loading-overlay/dist/vue-loading.css";
import Logout from "@/components/Logout.vue";

@Options({
  components: {
    Loading,
    Logout,
  },
  methods: {
    async updateAuthenticated() {
      this.loading = true;
      let response = await fetch(`/v1/api/authenticated`);
      this.loading = false;
      if (response.status == 200) {
        this.authenticated = true;
      }
    },
  },
  created() {
    this.updateAuthenticated();
  },
  data() {
    return {
      loading: false,
      authenticated: false,
    };
  },
})
export default class Home extends Vue {}
</script>

<style>
@import "scss/core.scss";

#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

#nav {
  padding: 30px;
}

#nav a {
  font-weight: bold;
  color: #2c3e50;
}

#nav a.router-link-exact-active {
  color: #42b983;
}

.center {
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  min-height: 100vh;
  margin: -20%;
}
</style>
