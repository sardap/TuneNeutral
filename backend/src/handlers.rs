#![deny(warnings)]

/// These are our API handlers, the ends of each filter chain.
/// Notice how thanks to using `Filter::and`, we can define a function
/// with the exact arguments we'd expect from each filter in the chain.
/// No tuples are needed, it's auto flattened for the functions.
use core::panic;
use futures_util::TryStreamExt;
use rspotify::{
    clients::{mutex::Mutex, OAuthClient},
    scopes, AuthCodeSpotify, Config, Credentials, OAuth,
};
use std::{convert::Infallible, env, fs, option::Option, path::PathBuf, str::FromStr, sync::Arc};
use uuid::Uuid;
use warp::{
    http::{header, HeaderValue, Response, StatusCode},
    hyper::Uri,
    Reply,
};

use crate::models::CallbackQuery;

const CACHE_PATH: &str = ".spotify_cache/";

fn get_cache_path(uuid: Option<String>) -> PathBuf {
    let project_dir_path = env::current_dir().unwrap();
    let mut cache_path = project_dir_path;
    cache_path.push(CACHE_PATH);
    cache_path.push(uuid.unwrap());

    cache_path
}

fn create_cache_path_if_absent(uuid: Option<String>) -> PathBuf {
    let cache_path = get_cache_path(uuid);
    if !cache_path.exists() {
        let mut path = cache_path.clone();
        path.pop();
        fs::create_dir_all(path).unwrap();
    }
    cache_path
}

fn init_spotify(uuid: Option<String>) -> AuthCodeSpotify {
    let config = Config {
        token_cached: true,
        cache_path: create_cache_path_if_absent(uuid),
        ..Default::default()
    };

    // Please notice that protocol of redirect_uri, make sure it's http
    // (or https). It will fail if you mix them up.
    let oauth = OAuth {
        scopes: scopes!("user-read-currently-playing", "playlist-modify-private"),
        redirect_uri: "http://localhost:3030/callback".to_owned(),
        ..Default::default()
    };

    // Replacing client_id and client_secret with yours.
    let creds = Credentials::new("", "");

    AuthCodeSpotify::with_config(creds, oauth, config)
}

pub async fn spotify_auth(uuid: Option<String>) -> Result<impl Reply, Infallible> {
    let (url, uuid) = match uuid {
        Some(uuid) => (Uri::from_static("/"), uuid),
        None => {
            let uuid_raw = Uuid::new_v4();
            let uuid = uuid_raw.as_u128().to_string();

            let spotify = init_spotify(Some(uuid.clone()));
            let auth_url = spotify.get_authorize_url(true).unwrap();

            // warp::redirect()
            (Uri::from_str(auth_url.as_str()).unwrap(), uuid)
        }
    };

    Ok(Response::builder()
        .status(StatusCode::TEMPORARY_REDIRECT)
        .header(header::SET_COOKIE, format!("uuid={}", uuid))
        .header(
            header::LOCATION,
            HeaderValue::from_str(url.to_string().as_str()).unwrap(),
        )
        .body(""))
}

pub async fn callback(
    uuid: Option<String>,
    query: CallbackQuery,
) -> Result<impl warp::Reply, Infallible> {
    let code: String;
    match query.code {
        Some(q_code) => {
            code = q_code;
        }
        None => {
            panic!("")
        }
    }

    match init_spotify(uuid).request_token(code.as_str()).await {
        Ok(_) => {
            println!("Request user token successful");
            Ok(warp::redirect(Uri::from_static("/")))
        }
        Err(err) => {
            println!("Failed to get user token {:?}", err);
            panic!("shit yeah");
        }
    }
}

pub async fn me(uuid: Option<String>) -> Result<impl Reply, Infallible> {
    let mut spotify = init_spotify(uuid);
    if !spotify.config.cache_path.exists() {
        let x = vec![0];
        return Ok(warp::reply::json(&x));
    }

    spotify.token = Arc::new(Mutex::new(spotify.read_token_cache(true).await.unwrap()));
    match spotify.me().await {
        Ok(user_info) => Ok(warp::reply::json(&user_info)),
        Err(err) => {
            println!("err {}", err);
            let x = vec![0];
            Ok(warp::reply::json(&x))
        }
    }
}

async fn get_tracks(spotify: &AuthCodeSpotify) {
    // Executing the futures concurrently
    let stream = spotify.current_user_saved_tracks(None);
    println!("\nItems (concurrent):");
    stream
        .try_for_each_concurrent(10, |item| async move {
            println!("* {}", item.track.name);
            Ok(())
        })
        .await
        .unwrap();

    // Executing the futures sequentially
    // let stream = spotify.current_user_saved_tracks(None);
    // pin_mut!(stream);
    // println!("Items (blocking):");
    // while let Some(item) = stream.try_next().await.unwrap() {
    //     println!("* {}", item.track.name);
    // }
}

pub async fn playlist(uuid: Option<String>) -> Result<impl Reply, Infallible> {
    let mut spotify = init_spotify(uuid);
    if !spotify.config.cache_path.exists() {
        let x = vec![0];
        return Ok(warp::reply::json(&x));
    }

    spotify.token = Arc::new(Mutex::new(spotify.read_token_cache(true).await.unwrap()));
    get_tracks(&spotify).await;

    let x = vec![0];
    Ok(warp::reply::json(&x))
}

/*
pub async fn list_todos(opts: ListOptions, db: Db) -> Result<impl warp::Reply, Infallible> {
    // Just return a JSON array of todos, applying the limit and offset.
    let todos = db.lock().await;
    let todos: Vec<Todo> = todos
        .clone()
        .into_iter()
        .skip(opts.offset.unwrap_or(0))
        .take(opts.limit.unwrap_or(std::usize::MAX))
        .collect();
    Ok(warp::reply::json(&todos))
}

pub async fn create_todo(create: Todo, db: Db) -> Result<impl warp::Reply, Infallible> {
    log::debug!("create_todo: {:?}", create);

    let mut vec = db.lock().await;

    for todo in vec.iter() {
        if todo.id == create.id {
            log::debug!("    -> id already exists: {}", create.id);
            // Todo with id already exists, return `400 BadRequest`.
            return Ok(StatusCode::BAD_REQUEST);
        }
    }

    // No existing Todo with id, so insert and return `201 Created`.
    vec.push(create);

    Ok(StatusCode::CREATED)
}

pub async fn update_todo(id: u64, update: Todo, db: Db) -> Result<impl warp::Reply, Infallible> {
    log::debug!("update_todo: id={}, todo={:?}", id, update);
    let mut vec = db.lock().await;

    // Look for the specified Todo...
    for todo in vec.iter_mut() {
        if todo.id == id {
            *todo = update;
            return Ok(StatusCode::OK);
        }
    }

    log::debug!("    -> todo id not found!");

    // If the for loop didn't return OK, then the ID doesn't exist...
    Ok(StatusCode::NOT_FOUND)
}

pub async fn delete_todo(id: u64, db: Db) -> Result<impl warp::Reply, Infallible> {
    log::debug!("delete_todo: id={}", id);

    let mut vec = db.lock().await;

    let len = vec.len();
    vec.retain(|todo| {
        // Retain all Todos that aren't this id...
        // In other words, remove all that *are* this id...
        todo.id != id
    });

    // If the vec is smaller, we found and deleted a Todo!
    let deleted = vec.len() != len;

    if deleted {
        // respond with a `204 No Content`, which means successful,
        // yet no body expected...
        Ok(StatusCode::NO_CONTENT)
    } else {
        log::debug!("    -> todo id not found!");
        Ok(StatusCode::NOT_FOUND)
    }
}
*/
