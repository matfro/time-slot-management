package pl.mfro.doodle

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import spock.lang.Specification

@SpringBootTest
@Import(TestcontainersConfiguration)
class DoodleApplicationTests extends Specification {

  def "context loads properly"() {
    expect:
    true
  }
}
