package pl.mfro.doodle

import org.springframework.boot.SpringApplication

class TestDoodleApplication {

  static void main(String[] args) {
    SpringApplication.from(DoodleApplication::main).with(TestcontainersConfiguration.class).run(args)
  }
}
