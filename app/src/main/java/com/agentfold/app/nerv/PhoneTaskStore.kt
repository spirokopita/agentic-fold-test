package com.agentfold.app.nerv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneTaskStore(context: Context) {

    enum class TaskStatus { PROPOSED, RUNNING, DONE, DENIED, STOPPED, FAILED }

    data class PhoneTask(
        val id: String,
        val objective: String,
        val question: String,
        var status: TaskStatus,
        val createdAt: String,
        var result: String,

        val createdAtMillis: Long = 0L
    )

    companion object {
        const val MAX_TASKS = 20
        const val MAX_OBJECTIVE_CHARS = 2000
        const val MAX_RESULT_CHARS = 2000
        private const val TASKS_JSON_KEY = "phone_tasks_json"
    }

    private val prefs = context.getSharedPreferences("nerv_phone_tasks", Context.MODE_PRIVATE)

    private val tasks: MutableList<PhoneTask> = loadTasks().toMutableList()

    fun all(): List<PhoneTask> = synchronized(tasks) { tasks.toList() }

    fun latest(): PhoneTask? = synchronized(tasks) { tasks.firstOrNull() }

    fun running(): PhoneTask? = synchronized(tasks) { tasks.firstOrNull { it.status == TaskStatus.RUNNING } }

    fun get(id: String): PhoneTask? = synchronized(tasks) { tasks.firstOrNull { it.id == id } }

    fun propose(objectiveRaw: String, questionRaw: String): PhoneTask? {
        val objective = objectiveRaw.trim()
        if (objective.isEmpty()) return null
        val task = PhoneTask(
            id = "task-${System.currentTimeMillis()}",
            objective = objective.take(MAX_OBJECTIVE_CHARS),
            question = questionRaw.trim().take(500),
            status = TaskStatus.PROPOSED,
            createdAt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date()),
            result = "",
            createdAtMillis = System.currentTimeMillis()
        )
        synchronized(tasks) {

            while (tasks.size >= MAX_TASKS) {
                val droppable = tasks.indexOfLast {
                    it.status == TaskStatus.DONE || it.status == TaskStatus.DENIED ||
                        it.status == TaskStatus.STOPPED || it.status == TaskStatus.FAILED
                }
                if (droppable < 0) break
                tasks.removeAt(droppable)
            }
            if (tasks.size >= MAX_TASKS) return null
            tasks.add(0, task)
            save()
        }
        return task
    }

    fun markRunning(id: String): Boolean = setStatus(id, TaskStatus.RUNNING)

    fun markDone(id: String, resultRaw: String): Boolean {
        synchronized(tasks) {
            val task = tasks.firstOrNull { it.id == id } ?: return false
            task.status = TaskStatus.DONE
            task.result = resultRaw.trim().take(MAX_RESULT_CHARS)
            save()
            return true
        }
    }

    fun markDenied(id: String): Boolean = setStatus(id, TaskStatus.DENIED)

    fun markStopped(id: String): Boolean = setStatus(id, TaskStatus.STOPPED)

    fun markFailed(id: String, reasonRaw: String): Boolean {
        synchronized(tasks) {
            val task = tasks.firstOrNull { it.id == id } ?: return false
            task.status = TaskStatus.FAILED
            task.result = reasonRaw.trim().take(MAX_RESULT_CHARS)
            save()
            return true
        }
    }

    fun delete(id: String): Boolean {
        synchronized(tasks) {
            val task = tasks.firstOrNull { it.id == id } ?: return false
            tasks.remove(task)
            save()
            return true
        }
    }

    private fun setStatus(id: String, status: TaskStatus): Boolean {
        synchronized(tasks) {
            val task = tasks.firstOrNull { it.id == id } ?: return false
            task.status = status
            save()
            return true
        }
    }

    private fun loadTasks(): List<PhoneTask> {
        val raw = prefs.getString(TASKS_JSON_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = mutableListOf<PhoneTask>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val objective = obj.optString("objective", "").trim()
                if (id.isEmpty() || objective.isEmpty()) continue
                val status = runCatching { TaskStatus.valueOf(obj.optString("status", "PROPOSED")) }
                    .getOrDefault(TaskStatus.PROPOSED)
                out.add(PhoneTask(
                    id = id,
                    objective = objective,
                    question = obj.optString("question", ""),
                    status = status,
                    createdAt = obj.optString("createdAt", ""),
                    result = obj.optString("result", ""),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                ))
            }

            out.forEach { if (it.status == TaskStatus.RUNNING) it.status = TaskStatus.STOPPED }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            tasks.forEach { task ->
                array.put(JSONObject()
                    .put("id", task.id)
                    .put("objective", task.objective)
                    .put("question", task.question)
                    .put("status", task.status.name)
                    .put("createdAt", task.createdAt)
                    .put("createdAtMillis", task.createdAtMillis)
                    .put("result", task.result))
            }
            prefs.edit().putString(TASKS_JSON_KEY, array.toString()).apply()
        } catch (_: Exception) {   }
    }
}
