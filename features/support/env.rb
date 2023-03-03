BeforeAll do
  Maze.config.receive_no_requests_wait = 10
  Maze.config.receive_requests_wait = 60
end

Before('@skip') do |scenario|
  skip_this_scenario("Skipping scenario")
end
