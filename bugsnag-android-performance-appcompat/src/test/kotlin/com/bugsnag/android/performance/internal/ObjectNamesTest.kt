import com.bugsnag.android.performance.BugsnagName
import com.bugsnag.android.performance.internal.ObjectNames
import junit.framework.TestCase.assertEquals
import org.junit.Test

class ObjectNamesTest {
    @BugsnagName("CustomName")
    class AnnotatedClass

    class PlainClass

    @Test
    fun classWithAnnotation() {
        val objectNames = ObjectNames()
        val annotated = AnnotatedClass()
        assertEquals("CustomName", objectNames[annotated])
        // assert there are no visible side-effects
        assertEquals("CustomName", objectNames[annotated])
    }

    @Test
    fun classWithoutAnnotation() {
        val objectNames = ObjectNames()
        val plain = PlainClass()
        assertEquals("PlainClass", objectNames[plain])
    }
}
