package io.github.chorusfruit233.studywithmiku

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Exercise the native per-date task limit and real SharedPreferences without account traffic. */
class TaskPersistenceTest {
    @Test fun fullDateRejectsNewPlanWithoutChangingExistingPlans() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "task_limit_fixture_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(ignored: String,mode: Int) = target.getSharedPreferences(name,mode)
        }
        try {
            val date = LocalDate.of(2027,1,31)
            val seeded = JSONArray().apply {
                repeat(200) { index ->
                    put(JSONObject().put("id","plan-$index").put("text","Existing plan $index")
                        .put("date",date.toString()).put("done",index % 2 == 0)
                        .put("pinned",false).put("color","#f06292"))
                }
            }
            context.getSharedPreferences("miku_offline_v1",Context.MODE_PRIVATE).edit()
                .putString("todos",seeded.toString()).commit()
            val store = MikuStore(context)
            val original = store.todos.value.toList()
            val revision = store.revision.value

            assertNull(store.addTodo("Overflow plan",date))
            assertNull(store.addTodo("   ",date))
            assertEquals(original,store.todos.value)
            assertEquals(revision,store.revision.value)
            assertEquals(original,MikuStore(context).todos.value)

            // The limit applies to the selected date, so a global task can still be added.
            val id = store.addTodo("Undated task")
            assertNotNull(id)
            assertEquals(original,store.todos.value.filter { it.date == date.toString() })
            assertEquals("Undated task",store.todos.value.single { it.id == id }.text)
            assertEquals("",store.todos.value.single { it.id == id }.date)
            assertEquals(store.todos.value,MikuStore(context).todos.value)
        } finally { target.deleteSharedPreferences(name) }
    }
}
