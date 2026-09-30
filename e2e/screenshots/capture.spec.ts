import path from 'node:path'
import { expect, test, type APIRequestContext } from '@playwright/test'
import { API_URL } from '../tests/helpers'

const OUT = path.join(__dirname, '..', '..', 'docs', 'screenshots')
const RESUME = path.join(__dirname, '..', 'fixtures', 'demo-resume.pdf')

// Fictional companies and postings only.
const JOBS = [
  { company: 'Acme Robotics', title: 'Senior Backend Engineer', location: 'Remote', status: 'INTERVIEW', days: 5,
    description: '6+ years of experience. You will build Java and Spring Boot services on PostgreSQL, deploy with Docker and Kubernetes on AWS, and own CI/CD with GitHub Actions. Experience with Kafka is a plus.' },
  { company: 'Globex Corporation', title: 'Full-Stack Engineer', location: 'Berlin', status: 'SCREENING', days: 0,
    description: '4+ years building React and TypeScript front ends with a Node.js or Java back end. You will write tests with Jest and Playwright, use REST and GraphQL APIs, and work with PostgreSQL and Docker.' },
  { company: 'Initech', title: 'Platform Engineer', location: 'Austin', status: 'APPLIED', days: 0,
    description: 'Looking for 5 years of experience with AWS, Terraform, Kubernetes and Docker. You will run CI/CD pipelines with Jenkins, use Python and Linux daily, and improve reliability.' },
  { company: 'Umbrella Labs', title: 'Frontend Engineer', location: 'London', status: 'OFFER', days: 0,
    description: '3+ years of React, TypeScript, HTML and CSS. You will build accessible interfaces with Tailwind CSS, test with Jest and Playwright, and collaborate with designers.' },
  { company: 'Hooli Cloud', title: 'Software Engineer', location: 'Toronto', status: 'REJECTED', days: 0,
    description: 'Java, Spring Boot, MySQL, MongoDB and Redis. 4 years of experience designing REST APIs. Kafka and RabbitMQ experience preferred.' },
  { company: 'Stark Analytics', title: 'Backend Engineer', location: 'Remote', status: 'APPLIED', days: 0,
    description: 'Python, Django and PostgreSQL. 3+ years of experience. AWS and Docker required; Elasticsearch is a bonus.' },
  { company: 'Wayne Systems', title: 'Java Developer', location: 'Remote', status: 'SAVED', days: 0,
    description: 'Java, Spring Boot, JUnit and Mockito. Maven, Git and Linux. 2+ years of experience.' },
  { company: 'Pied Piper', title: 'Staff Engineer', location: 'Remote', status: 'WITHDRAWN', days: 0,
    description: '8+ years of experience. Java, Kotlin, Kubernetes, Terraform, AWS and Kafka.' },
]

async function api(request: APIRequestContext, token: string, method: 'post' | 'put', url: string, data?: unknown) {
  const response = await request[method](`${API_URL}${url}`, { headers: { Authorization: `Bearer ${token}` }, data })
  expect(response.ok(), `${method} ${url} → ${response.status()}`).toBeTruthy()
  return response.json()
}

test('seed a fictional account and capture the main screens', async ({ page, request, browser }) => {
  test.setTimeout(180_000)
  const email = `demo-${Date.now()}@example.com`
  await page.goto('/')
  await page.screenshot({ path: path.join(OUT, '01-landing.png'), fullPage: true })

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem('joblens.session') ?? '{}').token as string)

  // Resume through the real upload path.
  const upload = await request.post(`${API_URL}/api/resumes`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: { file: { name: 'alex-morgan-resume.pdf', mimeType: 'application/pdf', buffer: require('node:fs').readFileSync(RESUME) } },
  })
  expect(upload.status()).toBe(201)
  await api(request, token, 'put', '/api/users/me/profile', { name: 'Alex Morgan', headline: 'Full-stack engineer', location: 'Remote', yearsOfExperience: 7, summary: 'Builds reliable APIs and accessible interfaces.' })
  for (const [name, category, proficiency] of [['Java', 'LANGUAGE', 'EXPERT'], ['TypeScript', 'LANGUAGE', 'ADVANCED'], ['Spring Boot', 'FRAMEWORK', 'ADVANCED'], ['React', 'FRAMEWORK', 'ADVANCED'], ['PostgreSQL', 'DATABASE', 'ADVANCED'], ['Docker', 'DEVOPS', 'INTERMEDIATE']]) {
    await api(request, token, 'post', '/api/users/me/skills', { name, category, proficiency })
  }

  const now = Date.now()
  const created: { id: string; company: string }[] = []
  for (const [i, job] of JOBS.entries()) {
    const j = await api(request, token, 'post', '/api/jobs', { company: job.company, title: job.title, location: job.location, employmentType: 'FULL_TIME', jobDescription: job.description })
    created.push({ id: j.id, company: job.company })
    const app = await api(request, token, 'post', '/api/applications', { jobId: j.id, status: 'SAVED' })
    const walk = { SAVED: [], APPLIED: ['APPLIED'], SCREENING: ['APPLIED', 'SCREENING'], INTERVIEW: ['APPLIED', 'SCREENING', 'INTERVIEW'], OFFER: ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER'], REJECTED: ['APPLIED', 'SCREENING', 'REJECTED'], WITHDRAWN: ['APPLIED', 'WITHDRAWN'] }[job.status] as string[]
    for (const s of walk) await request.patch(`${API_URL}/api/applications/${app.id}/status`, { headers: { Authorization: `Bearer ${token}` }, data: { status: s } })
    const appliedAt = new Date(now - (i * 6 + 3) * 86_400_000).toISOString().slice(0, 10)
    await api(request, token, 'put', `/api/applications/${app.id}`, {
      status: job.status, appliedAt: job.status === 'SAVED' ? null : appliedAt,
      interviewDate: job.days ? new Date(now + job.days * 86_400_000 + 4 * 3_600_000).toISOString() : null,
      notes: job.status === 'INTERVIEW' ? 'Technical loop with the platform team. Review system design and Kafka basics.' : null,
    })
  }
  for (const job of created.slice(0, 4)) await api(request, token, 'post', `/api/jobs/${job.id}/analyze`)
  await api(request, token, 'post', `/api/jobs/${created[0].id}/interview-questions/generate`)
  const questions = await (await request.get(`${API_URL}/api/interviews/questions?jobId=${created[0].id}&size=3`, { headers: { Authorization: `Bearer ${token}` } })).json()
  await api(request, token, 'put', `/api/interviews/questions/${questions.content[0].id}`, { notes: 'Lead with the incident at Northwind: timeline, trade-off, outcome.', status: 'PREPARED' })
  await api(request, token, 'put', `/api/interviews/questions/${questions.content[1].id}`, { notes: null, status: 'IN_PROGRESS' })

  const shots: [string, string, (() => Promise<void>)?][] = [
    ['02-dashboard', '/dashboard'],
    ['03-analytics', '/analytics'],
    ['04-jobs', '/jobs'],
    ['05-applications', '/applications'],
    ['06-resume', '/resume'],
    ['07-profile', '/profile'],
    ['08-interview-prep', `/interview-prep?jobId=${created[0].id}`],
    ['09-job-analysis', `/jobs/${created[0].id}`],
  ]
  await page.setViewportSize({ width: 1280, height: 900 })
  for (const [name, url] of shots) {
    await page.goto(url)
    await page.waitForLoadState('networkidle')
    await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true })
  }

  // Responsive check: the same screens on a phone-sized viewport.
  const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const mobile = await phone.newPage()
  await mobile.addInitScript((t) => localStorage.setItem('joblens.session', t), await page.evaluate(() => localStorage.getItem('joblens.session') ?? ''))
  for (const [name, url] of [['m-dashboard', '/dashboard'], ['m-applications', '/applications'], ['m-job-analysis', `/jobs/${created[0].id}`]]) {
    await mobile.goto(url)
    await mobile.waitForLoadState('networkidle')
    await mobile.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true })
  }
  await phone.close()
})
