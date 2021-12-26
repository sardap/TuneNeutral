<template>
  <div class="vld-parent">
    <loading
      v-model:active="loading"
      :can-cancel="false"
      :is-full-page="true"
      blur="3px"
    />
    <div v-if="authenticated">
      <div class="navbar">
        <div class="content">
          <div class="desktop" v-if="is_desktop">
            <div id="icon-desktop">
              <a href="/">
                <img src="@/assets/icon.png" height="60" />
              </a>
            </div>
            <div id="links">
              <div id="nav">
                <router-link to="/">Report Mood</router-link> |
                <router-link to="/playlists">Playlists</router-link> |
                <router-link to="/my_data">My Data</router-link>
              </div>
            </div>
            <div id="logout">
              <Logout />
            </div>
            <p id="version">v{{ version }}</p>
          </div>
          <div class="mobile menu" v-else>
            <div id="icon-desktop" v-if="!menu_open">
              <a href="/">
                <img src="@/assets/icon.png" height="60" />
              </a>
            </div>
            <Slide
              right
              :closeOnNavigation="true"
              class="tune"
              @openMenu="menu_open = true"
              @closeMenu="menu_open = false"
            >
              <span>
                <a href="/">
                  <img src="@/assets/icon.png" height="50" />
                </a>
              </span>
              <span>
                <router-link to="/">Report Mood</router-link>
              </span>
              <span>
                <router-link to="/playlists">Playlists</router-link>
              </span>
              <span>
                <router-link to="/my_data">My Data</router-link>
              </span>
              <span>
                <Logout />
              </span>
              <span> v{{ version }} </span>
            </Slide>
          </div>
        </div>
      </div>
      <div id="content">
        <router-view />
      </div>
    </div>
    <div v-else class="center">
      <div>
        <img src="@/assets/icon.png" height="120" />
        <h1>Tune Neutral</h1>
        <div>
          <div class="terms_and_conditions">
            <input
              type="checkbox"
              name="terms_and_conditions"
              v-model="terms_and_conditions"
            />
            <label
              for="terms_and_conditions"
              v-on:click="terms_and_conditions = !terms_and_conditions"
            >
              I Agree that I am over 13 and That I Consent to Tune Neutral
              storing my liked tracks information and user id from
              Spotify.</label
            >
          </div>
          <br />
          <br />
          <div
            :key="terms_and_conditions"
            :class="`button get-started ${
              terms_and_conditions ? `active` : `inactive`
            }`"
            v-on:click="getStarted()"
          >
            Get started
          </div>
        </div>
        <About />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import Loading from "vue-loading-overlay";
import "vue-loading-overlay/dist/vue-loading.css";
import Logout from "@/components/Logout.vue";
import { Slide } from "vue3-burger-menu";
import { version } from "@/version";
import About from "@/components/About.vue";

@Options({
  components: {
    Loading,
    Logout,
    Slide,
    About,
  },
  methods: {
    getStarted() {
      if (this.terms_and_conditions) {
        window.location.href = `/auth?terms_and_conditions=${this.terms_and_conditions}`;
      }
    },
    async updateAuthenticated() {
      this.loading = true;
      let response = await fetch(`/v1/api/authenticated`);
      this.loading = false;
      if (response.status == 200) {
        this.authenticated = true;
      }
    },
    refreshSize() {
      this.is_desktop = window.innerWidth >= 630;
    },
  },
  created() {
    this.refreshSize();
    this.updateAuthenticated();
    window.addEventListener("resize", this.refreshSize);
  },
  destroyed() {
    window.removeEventListener("resize", this.refreshSize);
  },

  data() {
    return {
      loading: false,
      authenticated: false,
      is_desktop: false,
      menu_open: false,
      version: version,
      terms_and_conditions: false,
    };
  },
})
export default class Home extends Vue {}
</script>

<style>
@import "scss/core.scss";
@import url("https://fonts.googleapis.com/css2?family=Josefin+Sans:wght@400&display=swap");

#app {
  font-family: "Josefin Sans", sans-serif;
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
  margin-top: 5%;
}

#content {
  margin-top: 130px;
}

.navbar {
  background-color: #efecc5;
  border: 5px solid #ddefc5;
  position: fixed; /* fixing the position takes it out of html flow - knows
                   nothing about where to locate itself except by browser
                  coordinates */
  left: 0; /* top left corner should start at leftmost spot */
  top: 0; /* top left corner should start at topmost spot */
  width: 100vw; /* take up the full browser width */
  z-index: 200; /* high z index so other content scrolls underneath */
  height: 70px; /* define height for content */
}

.navbar .content {
  margin-top: 5px;
  margin-left: 2px;
  margin-right: 50px;
}

#icon-desktop {
  float: left;
}

#icon-mobile {
  float: right;
}

.navbar #links {
  float: left;
}

.navbar #logout {
  float: right;
  margin-top: 20px;
}

.get-started {
  font-size: 25px;
}

.mobile .menu {
  clear: left;
  float: right;
}

.tune .bm-burger-button {
  left: initial;
  right: 36px;
  top: 20px;
}

.bm-burger-bars {
  background-color: #373a47;
}
.line-style {
  position: absolute;
  height: 20%;
  left: 0;
  right: 0;
}
.cross-style {
  position: absolute;
  top: 12px;
  right: 2px;
  cursor: pointer;
}
.bm-cross {
  background: #bdc3c7;
}
.bm-cross-button {
  height: 24px;
  width: 24px;
}
.tune .bm-menu {
  height: 100%; /* 100% Full-height */
  width: 0; /* 0 width - change this with JavaScript */
  position: fixed; /* Stay in place */
  z-index: 1000; /* Stay on top */
  top: 0;
  left: 0;
  background-color: #e4df9d; /* Black*/
  overflow-x: hidden; /* Disable horizontal scroll */
  padding-top: 60px; /* Place content 60px from the top */
  transition: 0.5s; /*0.5 second transition effect to slide in the sidenav*/
}

.bm-item-list {
  color: #b8b7ad;
  margin-left: 10%;
  font-size: 20px;
}
.bm-item-list > * {
  display: flex;
  text-decoration: none;
  padding: 0.7em;
}
.bm-item-list > * > span {
  margin-left: 10px;
  font-weight: 700;
  color: white;
}

.desktop #version {
  float: left;
  margin-top: 30px;
}

.inactive {
  cursor: default;
  background-color: #cdc2c9;
}

.inactive:hover {
  cursor: default;
  background-color: #cdc2c9;
}

.terms_and_conditions {
  margin-left: 20%;
  margin-right: 20%;
}
</style>
