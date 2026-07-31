package studio.sculk.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import studio.sculk.annotation.SculkStable

/** The table an entity is stored in. Required. */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Table(val name: String)

/**
 * The primary key column. Exactly one property must carry it.
 *
 * Zero or two is a wiring bug that would otherwise surface as an upsert that silently inserts a
 * duplicate row on every save, so [TableSchema] fails loudly at start-up instead.
 */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Id

/** Overrides the column name a property maps to. */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Column(val name: String)

/**
 * Creates an index on this column.
 *
 * Without one, every query that is not by primary key is a full table scan — which is fine on a
 * developer's 40-row table and not fine on a production one.
 */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Index

/**
 * Stores a structured property as JSON text.
 *
 * For lists, maps and nested objects. Without it those would be written with `toString()`, which
 * reads back as a String that no longer parses.
 */
@SculkStable
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Json
