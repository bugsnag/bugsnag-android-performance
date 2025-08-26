BeforeAll do
  Maze.config.receive_no_requests_wait = 10
  Maze.config.receive_requests_wait = 60
end

Maze.hooks.before_all do
  if ENV['RUN_BENCHMARKS']
    # TODO: PLAT-14759 Do not use Maze.driver directly
    Maze.driver.execute_script('mobile: shell', command: 'cmd package compile -m speed -f com.bugsnag.benchmarks.android')
  end
end

Before('@skip') do |scenario|
  skip_this_scenario("Skipping scenario")
end

Before('@skip_above_android_9') do |scenario|
  skip_this_scenario("Skipping scenario") if Maze.config.os_version > 9
end

Before('@skip_below_android_10') do |scenario|
  skip_this_scenario("Skipping scenario") if Maze.config.os_version < 10
end