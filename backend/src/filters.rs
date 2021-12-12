#![deny(warnings)]

use crate::models::CallbackQuery;

use super::handlers;
use super::models::Db;
use warp::Filter;

/// The 4 TODOs filters combined.
pub fn filters(
    _db: Db,
) -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
    callback().or(api_login()).or(api_me()).or(api_playlist())
}

/// GET /auth
pub fn api_login() -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
    warp::path("auth")
        .and(warp::get())
        .and(warp::filters::cookie::optional("uuid"))
        .and_then(handlers::spotify_auth)
}

/// GET /callback
pub fn callback() -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
    warp::path("callback")
        .and(warp::get())
        .and(warp::filters::cookie::optional("uuid"))
        .and(warp::query::<CallbackQuery>())
        .and_then(handlers::callback)
}

/// GET /api/me
pub fn api_me() -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
    warp::path("api")
        .and(warp::path("me"))
        .and(warp::get())
        .and(warp::filters::cookie::optional("uuid"))
        .and_then(handlers::me)
}

/// GET /api/playlist
pub fn api_playlist() -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
    warp::path("api")
        .and(warp::path("playlist"))
        .and(warp::get())
        .and(warp::filters::cookie::optional("uuid"))
        .and_then(handlers::playlist)
}

// pub fn todos_list(
//     db: Db,
// ) -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
//     warp::path!("todos")
//         .and(warp::get())
//         .and(warp::query::<ListOptions>())
//         .and(with_db(db))
//         .and_then(handlers::list_todos)
// }

// /// POST /todos with JSON body
// pub fn todos_create(
//     db: Db,
// ) -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
//     warp::path!("todos")
//         .and(warp::post())
//         .and(json_body())
//         .and(with_db(db))
//         .and_then(handlers::create_todo)
// }

// /// PUT /todos/:id with JSON body
// pub fn todos_update(
//     db: Db,
// ) -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
//     warp::path!("todos" / u64)
//         .and(warp::put())
//         .and(json_body())
//         .and(with_db(db))
//         .and_then(handlers::update_todo)
// }

// /// DELETE /todos/:id
// pub fn todos_delete(
//     db: Db,
// ) -> impl Filter<Extract = impl warp::Reply, Error = warp::Rejection> + Clone {
//     // We'll make one of our endpoints admin-only to show how authentication filters are used
//     let admin_only = warp::header::exact("authorization", "Bearer admin");

//     warp::path!("todos" / u64)
//         // It is important to put the auth check _after_ the path filters.
//         // If we put the auth check before, the request `PUT /todos/invalid-string`
//         // would try this filter and reject because the authorization header doesn't match,
//         // rather because the param is wrong for that other path.
//         .and(admin_only)
//         .and(warp::delete())
//         .and(with_db(db))
//         .and_then(handlers::delete_todo)
// }

// fn with_db(db: Db) -> impl Filter<Extract = (Db,), Error = std::convert::Infallible> + Clone {
//     warp::any().map(move || db.clone())
// }

// fn json_body() -> impl Filter<Extract = (Todo,), Error = warp::Rejection> + Clone {
//     // When accepting a body, we want a JSON body
//     // (and to reject huge payloads)...
//     warp::body::content_length_limit(1024 * 16).and(warp::body::json())
// }
