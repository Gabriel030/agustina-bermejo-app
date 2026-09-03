function fn() {
  // KarateSuiteTest.setUp() setea esta system property con el puerto RANDOM_PORT real de
  // @SpringBootTest antes de correr cada .feature — no hay puerto fijo que hardcodear.
  var port = karate.properties['karate.server.port'];
  if (!port) {
    karate.fail('karate.server.port no seteado — este config asume que corre vía KarateSuiteTest, no standalone.');
  }
  var config = {
    baseUrl: 'http://localhost:' + port,
    // Password fija para cualquier alumno que un .feature registre vía /auth/register.
    passwordValida: 'Test1234'
  };
  return config;
}
